package com.recapmaker.app.media.rvc

import ai.onnxruntime.OrtSession
import android.util.Log

private const val TAG = "Rvc.Pipe"

class RvcPipeline(
    val metadata: ModelMetadata,
    private val embedder: HubertEmbedder,
    private val pitchExtractor: RmvpePitchExtractor?,
    private val synthesizer: RvcSynthesizer,
) : AutoCloseable {

    val outputSampleRate: Int get() = metadata.samplingRate

    fun convert(
        audio16k: FloatArray,
        f0UpKey: Int = 0,
        speakerId: Long = 0L,
        onProgress: (Float) -> Unit = {},
    ): FloatArray {
        val t0 = System.currentTimeMillis()
        Log.i(
            TAG,
            "convert: input=${audio16k.size} samples (${audio16k.size / 16}ms), " +
                "f0UpKey=$f0UpKey, sid=$speakerId, target=${metadata.samplingRate}Hz",
        )
        onProgress(0f)

        val emb = embedder.extract(audio16k)
        onProgress(0.4f)

        val pitch = if (metadata.f0) {
            requireNotNull(pitchExtractor) { "f0 model requires pitch extractor" }
            pitchExtractor.extract(audio16k, f0UpKey)
        } else null
        onProgress(0.6f)

        val frames2x = emb.frames * 2
        val feats2x = upsample2xNearest(emb.feats, emb.frames, emb.channels)

        val targetT = if (pitch != null) minOf(frames2x, pitch.pitchf.size) else frames2x
        val feats = if (frames2x == targetT) feats2x else feats2x.copyOf(targetT * emb.channels)
        val coarse = pitch?.pitchCoarse?.copyOf(targetT)
        val pitchf = pitch?.pitchf?.copyOf(targetT)

        Log.d(TAG, "  aligned T=$targetT, feats=${feats.size}, pitch=${coarse?.size}")
        onProgress(0.7f)

        val audio = synthesizer.infer(
            feats = feats,
            framesT = targetT,
            channels = emb.channels,
            pitch = coarse,
            pitchf = pitchf,
            speakerId = speakerId,
        )
        onProgress(1f)

        val elapsed = System.currentTimeMillis() - t0
        Log.i(
            TAG,
            "convert: done audio=${audio.size} samples @ ${metadata.samplingRate}Hz in ${elapsed}ms",
        )
        return audio
    }

    override fun close() {
        Log.d(TAG, "close")
        // Sessions are owned by the caller's cache (see RvcVoiceCloner)
    }

    // HuBERT emits at ~50fps (one frame per 320 samples of 16 kHz audio); the
    // synthesizer expects ~100fps (window=160). Voice-changer bridges the two
    // with F.interpolate(scale_factor=2) at mode='nearest' — each input frame
    // replicated to two output frames.
    private fun upsample2xNearest(feats: FloatArray, frames: Int, channels: Int): FloatArray {
        val out = FloatArray(frames * 2 * channels)
        for (t in 0 until frames) {
            val src = t * channels
            val dst = t * 2 * channels
            System.arraycopy(feats, src, out, dst, channels)
            System.arraycopy(feats, src, out, dst + channels, channels)
        }
        return out
    }
}

object RvcPipelineFactory {
    /**
     * Assemble a pipeline from already-opened ORT sessions. The caller owns
     * the sessions — they are not closed by [RvcPipeline.close].
     */
    fun assemble(
        synthSession: OrtSession,
        synthMetadata: ModelMetadata,
        hubertSession: OrtSession,
        rmvpeSession: OrtSession?,
    ): RvcPipeline {
        if (synthMetadata.f0) {
            requireNotNull(rmvpeSession) { "f0 model selected but no rmvpe session provided" }
        }
        val hubertOutput = chooseHubertOutput(synthMetadata)
        Log.i(TAG, "assemble: hubertOutput=$hubertOutput, f0=${synthMetadata.f0}")
        return RvcPipeline(
            metadata = synthMetadata,
            embedder = HubertEmbedder(hubertSession, hubertOutput),
            pitchExtractor = rmvpeSession?.let { RmvpePitchExtractor(it) },
            synthesizer = RvcSynthesizer(synthSession, synthMetadata.f0),
        )
    }

    // voice-changer's content_vec_500.onnx exposes three pre-baked outputs at
    // once: units9 (layer 9 + final_proj, 256d, v1 path), unit12 (layer 12
    // raw, 768d, v2 path), and unit12s (layer 12 + final_proj). The synth's
    // metadata tells us which one its TextEncoder was trained against.
    private fun chooseHubertOutput(meta: ModelMetadata): String = when {
        meta.embOutputLayer == 12 && !meta.useFinalProj -> "unit12"
        meta.embOutputLayer == 9 && meta.useFinalProj -> "units9"
        meta.embOutputLayer == 12 && meta.useFinalProj -> "unit12s"
        else -> error(
            "unsupported embedder config: layer=${meta.embOutputLayer}, finalProj=${meta.useFinalProj}",
        )
    }
}

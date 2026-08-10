package com.recapmaker.app.media.rvc

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import java.io.Closeable

private const val TAG = "Rvc.Synth"
private const val AUDIO_OUTPUT = "audio"

class RvcSynthesizer(
    private val session: OrtSession,
    private val hasF0: Boolean,
) : Closeable {

    // Most exports name the embedding input "feats" (voice-changer / WIAWAN);
    // a few call it "source"/"input". sid/p_len are optional — some generators
    // are single-speaker and omit them entirely.
    private val featsName: String = pickName(listOf("feats", "source", "input"), session.inputInfo.keys) ?: ""
    private val pLenName: String? = pickName(listOf("p_len", "length"), session.inputInfo.keys)
    private val sidName: String? = pickName(listOf("sid", "speaker", "spk"), session.inputInfo.keys)
    private val pitchName: String? = pickName(listOf("pitch", "f0"), session.inputInfo.keys)
    private val pitchfName: String? = pickName(listOf("pitchf", "f0f"), session.inputInfo.keys)

    private val featsIsFp16: Boolean = featsType(session) == OnnxJavaType.FLOAT16
    private val audioIsFp16: Boolean = audioType(session) == OnnxJavaType.FLOAT16

    init {
        require(featsName.isNotBlank()) { "synth has no feats-like input (${session.inputInfo.keys})" }
        if (hasF0) {
            requireNotNull(pitchName) { "f0 synth is missing a pitch input (${session.inputInfo.keys})" }
            requireNotNull(pitchfName) { "f0 synth is missing a pitchf input (${session.inputInfo.keys})" }
        }
        Log.i(
            TAG,
            "init: hasF0=$hasF0 feats=$featsName p_len=$pLenName sid=$sidName " +
                "pitch=$pitchName pitchf=$pitchfName fp16(feats/audio)=$featsIsFp16/$audioIsFp16",
        )
    }

    fun infer(
        feats: FloatArray,
        framesT: Int,
        channels: Int,
        pitch: LongArray? = null,
        pitchf: FloatArray? = null,
        speakerId: Long = 0L,
    ): FloatArray {
        val t0 = System.currentTimeMillis()
        require(feats.size == framesT * channels) {
            "feats size ${feats.size} != $framesT * $channels"
        }
        val env = OrtRuntime.env
        val featsShape = longArrayOf(1L, framesT.toLong(), channels.toLong())
        val tShape = longArrayOf(1L, framesT.toLong())
        val inputs = mutableMapOf<String, OnnxTensor>()
        try {
            inputs[featsName] =
                if (featsIsFp16) env.float16Tensor(feats, featsShape)
                else env.floatTensor(feats, featsShape)
            pLenName?.let { inputs[it] = env.longTensor(longArrayOf(framesT.toLong()), longArrayOf(1L)) }
            sidName?.let { inputs[it] = env.longTensor(longArrayOf(speakerId), longArrayOf(1L)) }
            if (hasF0) {
                inputs[pitchName!!] = env.longTensor(pitch!!, tShape)
                inputs[pitchfName!!] = env.floatTensor(pitchf!!, tShape)
            }
            // Some voice-changer exports surface debug intermediates (Mul_*,
            // Slice_*, RandomNormalLike_*, …) in addition to "audio" — pulling
            // only what we need keeps inference cheap and the result mapping
            // robust to schema additions.
            session.run(inputs, setOf(AUDIO_OUTPUT)).use { result ->
                val tensor = result.iterator().next().value as OnnxTensor
                val audio =
                    if (audioIsFp16) tensor.copyFloats16() else tensor.copyFloats()
                clipInPlace(audio)
                val elapsed = System.currentTimeMillis() - t0
                Log.i(
                    TAG,
                    "infer: feats[$framesT,$channels] f0=$hasF0 sid=$speakerId → audio[${audio.size}] in ${elapsed}ms",
                )
                return audio
            }
        } finally {
            inputs.values.forEach { runCatching { it.close() } }
        }
    }

    override fun close() {
        Log.d(TAG, "close")
        session.close()
    }

    private fun pickName(candidates: List<String>, keys: Set<String>): String? =
        candidates.firstOrNull { it in keys }

    private fun clipInPlace(out: FloatArray) {
        for (i in out.indices) {
            val v = out[i]
            if (v > 1f) out[i] = 1f else if (v < -1f) out[i] = -1f
        }
    }

    private companion object {
        fun featsType(session: OrtSession): OnnxJavaType? =
            (session.inputInfo["feats"]?.info as? TensorInfo)?.type
                ?: (session.inputInfo.values.firstOrNull()?.info as? TensorInfo)?.type

        fun audioType(session: OrtSession): OnnxJavaType? =
            (session.outputInfo[AUDIO_OUTPUT]?.info as? TensorInfo)?.type
                ?: (session.outputInfo.values.firstOrNull()?.info as? TensorInfo)?.type
    }
}

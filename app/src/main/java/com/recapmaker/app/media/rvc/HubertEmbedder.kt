package com.recapmaker.app.media.rvc

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import java.io.Closeable

private const val TAG = "Rvc.HuBERT"

class EmbeddingData(val feats: FloatArray, val frames: Int, val channels: Int) {
    val shape: LongArray get() = longArrayOf(1L, frames.toLong(), channels.toLong())
}

class HubertEmbedder(
    private val session: OrtSession,
    preferredOutput: String,
) : Closeable {

    // Different exports name the audio input differently ("audio" in
    // voice-changer's content_vec_500, "source" in plain hubert_base exports,
    // "input" in various community exports) — pick whichever one this model has.
    private val inputName: String = pickInputName(session.inputInfo.keys)
    private val outputName: String =
        if (preferredOutput in session.outputInfo.keys) preferredOutput
        else session.outputInfo.keys.firstOrNull()
            ?: error("hubert ONNX has no outputs")

    // Fixed-window exports (e.g. hubert_base_layer12_32000.onnx) accept exactly
    // `windowSamples` per call and emit `windowFrames` features. Dynamic exports
    // accept any length — detect which kind we have so inference never fails on
    // a shape mismatch. A fixed dim > 0 in the input shape means fixed-window.
    private val fixedWindowSamples: Int =
        ((session.inputInfo[inputName]?.info as? TensorInfo)?.shape?.getOrNull(1) ?: -1L)
            .takeIf { it > 0 }?.toInt() ?: 0

    // The number of feature rows the model emits for one full window — read from
    // the output shape (fixed exports pin it, e.g. [1, 99, 768]) with a fallback
    // to the 320-sample-per-frame rule of the fairseq conv feature extractor.
    private val framesPerWindow: Int =
        ((session.outputInfo[outputName]?.info as? TensorInfo)?.shape?.getOrNull(1) ?: -1L)
            .takeIf { it > 0 }?.toInt()
            ?: if (fixedWindowSamples > 0) convFrames(fixedWindowSamples) else 0

    init {
        require(inputName.isNotBlank()) { "hubert ONNX has no usable inputs" }
        Log.i(
            TAG,
            "init: input=$inputName output=$outputName fixedWindow=${fixedWindowSamples}",
        )
    }

    fun extract(audio16k: FloatArray): EmbeddingData {
        val t0 = System.currentTimeMillis()
        val env = OrtRuntime.env
        return if (fixedWindowSamples > 0) {
            extractChunked(env, audio16k, t0)
        } else {
            extractSingle(env, audio16k, t0)
        }
    }

    private fun extractSingle(env: ai.onnxruntime.OrtEnvironment, audio16k: FloatArray, t0: Long): EmbeddingData {
        env.floatTensor(audio16k, longArrayOf(1L, audio16k.size.toLong())).use { audio ->
            session.run(mapOf(inputName to audio), setOf(outputName)).use { result ->
                val tensor = result.iterator().next().value as OnnxTensor
                val shape = (tensor.info as TensorInfo).shape
                require(shape.size == 3 && shape[0] == 1L) {
                    "expected feats [1, T, C], got ${shape.contentToString()}"
                }
                val frames = shape[1].toInt()
                val channels = shape[2].toInt()
                val feats = tensor.copyFloats()
                val elapsed = System.currentTimeMillis() - t0
                Log.i(
                    TAG,
                    "extract: audio=${audio16k.size} → $outputName[1, $frames, $channels] in ${elapsed}ms",
                )
                return EmbeddingData(feats, frames, channels)
            }
        }
    }

    // Fixed-window models process ~2 s of 16 kHz audio per call (32000 samples →
    // 99 frames). Split the clip into windows, run each, and concatenate the
    // features. The final partial window is zero-padded; its feature rows are
    // trimmed to the frames that correspond to real audio (via the same conv
    // cascade the model uses).
    private fun extractChunked(
        env: ai.onnxruntime.OrtEnvironment,
        audio16k: FloatArray,
        t0: Long,
    ): EmbeddingData {
        val window = fixedWindowSamples
        require(window > 0) { "fixed-window hubert has invalid window size" }
        require(framesPerWindow > 0) { "cannot derive frames per window for $outputName" }

        val chunks = (audio16k.size + window - 1) / window
        if (chunks == 0) return EmbeddingData(FloatArray(0), 0, 768)

        var totalFrames = 0
        var channels = 768
        val parts = ArrayList<FloatArray>(chunks)
        val partFrames = ArrayList<Int>(chunks)

        val padded = FloatArray(window)
        for (c in 0 until chunks) {
            val start = c * window
            val real = minOf(window, audio16k.size - start)
            // Zero-pad the tail window (padding samples contribute nothing the
            // synthesizer will hear; we trim those frames below).
            padded.fill(0f)
            System.arraycopy(audio16k, start, padded, 0, real)
            env.floatTensor(padded, longArrayOf(1L, window.toLong())).use { audio ->
                session.run(mapOf(inputName to audio), setOf(outputName)).use { result ->
                    val tensor = result.iterator().next().value as OnnxTensor
                    val shape = (tensor.info as TensorInfo).shape
                    require(shape.size == 3 && shape[0] == 1L) {
                        "expected feats [1, T, C], got ${shape.contentToString()}"
                    }
                    channels = shape[2].toInt()
                    val feats = tensor.copyFloats()
                    // Keep only rows belonging to real audio in the tail window.
                    val keep = if (real == window) framesPerWindow
                    else minOf(framesPerWindow, convFrames(real))
                    parts.add(feats)
                    partFrames.add(keep)
                    totalFrames += keep
                }
            }
        }

        val out = FloatArray(totalFrames * channels)
        var off = 0
        for (i in parts.indices) {
            val keep = partFrames[i]
            System.arraycopy(parts[i], 0, out, off, keep * channels)
            off += keep * channels
        }
        val elapsed = System.currentTimeMillis() - t0
        Log.i(
            TAG,
            "extract(chunked): audio=${audio16k.size} → $outputName[1, $totalFrames, $channels] " +
                "($chunks windows) in ${elapsed}ms",
        )
        return EmbeddingData(out, totalFrames, channels)
    }

    // fairseq hubert conv feature extractor frame count: conv0 (k=10,s=5) then
    // four (k=3,s=2) then two (k=2,s=2). Returns how many feature rows `samples`
    // produce — 32000 → 99 for the standard export.
    private fun convFrames(samples: Int): Int {
        var n = (samples - 10) / 5 + 1
        repeat(4) { n = (n - 3) / 2 + 1 }
        repeat(2) { n = (n - 2) / 2 + 1 }
        return n.coerceAtLeast(0)
    }

    override fun close() {
        Log.d(TAG, "close")
        session.close()
    }

    private fun pickInputName(keys: Set<String>): String {
        if (keys.isEmpty()) return ""
        for (k in listOf("audio", "input", "source", "wav", "waveform", "data")) {
            if (k in keys) return k
        }
        return keys.first()
    }
}

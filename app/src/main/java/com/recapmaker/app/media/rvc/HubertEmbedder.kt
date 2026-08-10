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

    init {
        require(inputName.isNotBlank()) { "hubert ONNX has no usable inputs" }
        Log.i(TAG, "init: input=$inputName output=$outputName")
    }

    fun extract(audio16k: FloatArray): EmbeddingData {
        val t0 = System.currentTimeMillis()
        val env = OrtRuntime.env

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

package com.recapmaker.app.media.rvc

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import java.io.Closeable
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private const val TAG = "Rvc.RMVPE"

private const val F0_MIN = 50f
private const val F0_MAX = 1100f
private val F0_MEL_MIN = 1127f * ln(1f + F0_MIN / 700f)
private val F0_MEL_MAX = 1127f * ln(1f + F0_MAX / 700f)

class PitchData(val pitchf: FloatArray, val pitchCoarse: LongArray)

class RmvpePitchExtractor(private val session: OrtSession) : Closeable {

    // Two common exports: {"waveform","threshold"} (voice-changer style) or a
    // single {"input"} (lj1995/WIAWAN style). Pick whichever this model has.
    private val wavInput: String = pickWavInput()
    private val thrInput: String? = if ("threshold" in session.inputInfo.keys) "threshold" else null
    private val outputName: String = session.outputInfo.keys.firstOrNull() ?: ""

    init {
        Log.i(TAG, "init: wav=$wavInput thr=$thrInput out=$outputName")
    }

    fun extract(
        audio16k: FloatArray,
        f0UpKey: Int = 0,
        threshold: Float = 0.3f,
    ): PitchData {
        val t0 = System.currentTimeMillis()
        val env = OrtRuntime.env
        val inputs = mutableMapOf<String, OnnxTensor>()
        try {
            inputs[wavInput] = env.floatTensor(audio16k, longArrayOf(1L, audio16k.size.toLong()))
            thrInput?.let { inputs[it] = env.floatTensor(floatArrayOf(threshold), longArrayOf(1L)) }
            val outputSet = if (outputName.isNotBlank()) setOf(outputName) else null
            session.run(inputs, outputSet).use { result ->
                val pitchTensor = result.iterator().next().value as OnnxTensor
                val raw = pitchTensor.copyFloats()
                val shifted = if (f0UpKey == 0) raw else shift(raw, f0UpKey)
                val coarse = melQuantize(shifted)
                val elapsed = System.currentTimeMillis() - t0
                Log.i(
                    TAG,
                    "extract: audio=${audio16k.size} samples → pitchf[${shifted.size}] " +
                        "(voiced=${coarse.count { it > 1 }}/${coarse.size}) in ${elapsed}ms",
                )
                return PitchData(shifted, coarse)
            }
        } finally {
            inputs.values.forEach { runCatching { it.close() } }
        }
    }

    override fun close() {
        Log.d(TAG, "close")
        session.close()
    }

    private fun pickWavInput(): String {
        val keys = session.inputInfo.keys
        for (k in listOf("waveform", "input", "source", "audio", "wav")) {
            if (k in keys) return k
        }
        return keys.firstOrNull() ?: error("rmvpe ONNX has no inputs")
    }

    private fun shift(pitchf: FloatArray, semitones: Int): FloatArray {
        val factor = 2.0.pow(semitones / 12.0).toFloat()
        return FloatArray(pitchf.size) { pitchf[it] * factor }
    }

    // Match voice-changer's f0_coarse formula bit-for-bit so the synthesizer
    // sees the same pitch-class indices it was trained against.
    private fun melQuantize(pitchf: FloatArray): LongArray {
        val span = F0_MEL_MAX - F0_MEL_MIN
        val out = LongArray(pitchf.size)
        for (i in pitchf.indices) {
            val f = pitchf[i]
            val scaled = if (f > 0f) {
                val mel = 1127f * ln(1f + f / 700f)
                if (mel > 0f) (mel - F0_MEL_MIN) * 254f / span + 1f else 1f
            } else 1f
            out[i] = scaled.coerceIn(1f, 255f).roundToInt().toLong()
        }
        return out
    }
}

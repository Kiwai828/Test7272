package com.recapmaker.app.media.rvc

import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device RVC voice cloning — completely free, no API key, works offline.
 *
 * Pipeline: FFmpeg decode (mono 16 kHz f32) → HuBERT features → RMVPE pitch →
 * RVC synthesizer → WAV → AAC (m4a) for muxing into the final video.
 *
 * Requires three ONNX models exported with w-okada/voice-changer's
 * export2onnx.py: the synth generator (with embedded metadata), a HuBERT /
 * contentvec embedder, and the RMVPE pitch extractor.
 */
object RvcVoiceCloner {
    private const val TAG = "RvcVoiceCloner"
    private const val INPUT_RATE = 16000

    sealed class Result {
        data class Success(val file: File) : Result()
        data class Error(val message: String) : Result()
    }

    // Keep models warm across conversions with the same files
    private var cachedSynth: OrtSession? = null
    private var cachedHubert: OrtSession? = null
    private var cachedRmvpe: OrtSession? = null
    private var cachedPipeline: RvcPipeline? = null
    private var cacheKey: String = ""

    private fun fileKey(vararg files: File): String =
        files.joinToString("|") { "${it.absolutePath}:${it.length()}" }

    suspend fun convert(
        context: Context,
        inputAudio: File,
        synthModel: File,
        hubertModel: File,
        rmvpeModel: File? = null,
        f0UpKey: Int = 0,
    ): Result = withContext(Dispatchers.IO) {
        try {
            requireFiles(inputAudio, synthModel, hubertModel)

            // 1. Decode TTS audio → mono 16 kHz f32 via FFmpeg
            val raw = File(context.cacheDir, "rvc_in_${System.currentTimeMillis()}.raw")
            val dec = FFmpegKit.execute(
                "-y -i ${inputAudio.absolutePath} -vn -f f32le -ar $INPUT_RATE -ac 1 ${raw.absolutePath}",
            )
            if (!ReturnCode.isSuccess(dec.returnCode) || !raw.exists() || raw.length() < 4L) {
                raw.delete()
                return@withContext Result.Error("RVC input decode မအောင်မြင်ပါ")
            }
            val floats = readF32(raw)
            raw.delete()
            if (floats.isEmpty()) return@withContext Result.Error("Audio ဗလာ")

            // 2. Build / reuse the pipeline (sessions cached by model identity)
            val key = fileKey(synthModel, hubertModel)
            val pipeline = if (cacheKey == key && cachedPipeline != null) {
                cachedPipeline!!
            } else {
                releaseCache()
                val synth = OrtRuntime.openSession(synthModel)
                val metadata = ModelMetadata.fromSession(synth) ?: run {
                    runCatching { synth.close() }
                    return@withContext Result.Error("Synth model မှာ metadata မပါ — voice-changer (export2onnx.py) နဲ့ ထုတ်ရပါမည်")
                }
                val hubert = OrtRuntime.openSession(hubertModel)
                val rmvpe = if (metadata.f0) {
                    if (rmvpeModel == null) {
                        runCatching { synth.close() }; runCatching { hubert.close() }
                        return@withContext Result.Error("ဒီ model က f0 (pitch) သုံးတာမို့ RMVPE model ပါ ရွေးပေးပါ")
                    }
                    OrtRuntime.openSession(rmvpeModel)
                } else null
                val pipe = RvcPipelineFactory.assemble(synth, metadata, hubert, rmvpe)
                cachedSynth = synth; cachedHubert = hubert; cachedRmvpe = rmvpe
                cachedPipeline = pipe; cacheKey = key
                pipe
            }

            // 3. Voice conversion
            val out = pipeline.convert(floats, f0UpKey = f0UpKey)

            // 4. WAV → AAC m4a (matches the video muxer's expectations)
            val wav = File(context.cacheDir, "rvc_out_${System.currentTimeMillis()}.wav")
            wav.outputStream().use { WavIo.write(it, out, pipeline.outputSampleRate) }
            val m4a = File(context.cacheDir, "rvc_out_${System.currentTimeMillis()}.m4a")
            val enc = FFmpegKit.execute("-y -i ${wav.absolutePath} -c:a aac -b:a 128k ${m4a.absolutePath}")
            wav.delete()
            if (ReturnCode.isSuccess(enc.returnCode) && m4a.exists() && m4a.length() > 0L) {
                Result.Success(m4a)
            } else {
                m4a.delete()
                Result.Error("RVC output encode မအောင်မြင်ပါ")
            }
        } catch (e: Exception) {
            Log.e(TAG, "convert error: ${e.message}")
            Result.Error(e.message ?: "RVC error")
        }
    }

    private fun requireFiles(vararg files: File) {
        files.forEach { if (!it.exists() || it.length() == 0L) error("Model/audio file မရှိ: ${it.name}") }
    }

    private fun readF32(raw: File): FloatArray {
        val bytes = raw.readBytes()
        val out = FloatArray(bytes.size / 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out)
        return out
    }

    private fun releaseCache() {
        cachedPipeline = null
        runCatching { cachedSynth?.close() }
        runCatching { cachedHubert?.close() }
        runCatching { cachedRmvpe?.close() }
        cachedSynth = null; cachedHubert = null; cachedRmvpe = null
        cacheKey = ""
    }
}

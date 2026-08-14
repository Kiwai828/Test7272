package com.recapmaker.app.media

import android.content.Context
import android.util.Base64
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.recapmaker.app.data.repository.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Direct client for the deployed VoxCPM2 Modal functions.
 *
 * The current Modal deployment is URL-only: no Cloudflare relay and no bearer
 * token are used. Normal voice cloning calls the clone function directly.
 */
object VoxCpmClient {
    private const val CLONE_URL = "https://kouwuq--voxcpm2-tts-voxcpm2model-clone.modal.run"
    private const val HEALTH_URL = "https://kouwuq--voxcpm2-tts-voxcpm2model-health.modal.run"
    private const val MAX_TEXT_CHARS = 500
    private const val MAX_ATTEMPTS = 2
    private const val MAX_ERROR_BODY = 320
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val json = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .callTimeout(310, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val healthHttp = http.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    /** Check the deployed GPU/model health before coins are deducted. */
    suspend fun preflight(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(HEALTH_URL)
                .header("Accept", "application/json")
                .get()
                .build()
            healthHttp.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use "Modal health check မအောင်မြင်ပါ: ${parseError(response.code, body)}"
                }
                val lower = body.lowercase()
                if (lower.contains("not loaded") ||
                    lower.contains("\"model_loaded\":false") ||
                    lower.contains("\"ready\":false") ||
                    lower.contains("\"status\":\"unhealthy\"")) {
                    return@use "Modal VoxCPM2 model မ ready ဖြစ်သေးပါ။ ခဏစောင့်ပြီး ပြန်စမ်းပါ။"
                }
                null
            }
        }.getOrElse { e ->
            "Modal health service သို့ ဆက်သွယ်မရပါ: ${e.message ?: "network error"}"
        }
    }

    suspend fun isAvailable(): Boolean = preflight() == null

    suspend fun generate(
        context: Context,
        text: String,
        referenceAudio: File,
        styleControl: String = "Natural spoken Burmese narration, clear pronunciation and steady pacing.",
        cfgValue: Float = 2.0f,
        inferenceTimesteps: Int = 10,
        seed: Int? = null,
    ): Result<File> = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return@withContext Result.Error("VoxCPM2 သို့ပို့ရန် စာသားမရှိပါ")
        if (cleanText.length > MAX_TEXT_CHARS) {
            return@withContext Result.Error("Modal request တစ်ကြိမ်လျှင် စာလုံး ${MAX_TEXT_CHARS} အထိသာ ပို့နိုင်ပါသည်")
        }
        if (!referenceAudio.exists() || referenceAudio.length() == 0L) {
            return@withContext Result.Error("VoxCPM2 voice sample မတွေ့ပါ")
        }

        var wavReference: File? = null
        try {
            wavReference = prepareWavReference(context, referenceAudio)
            val referenceBase64 = Base64.encodeToString(wavReference.readBytes(), Base64.NO_WRAP)
            var lastError = "မသိရသေးသော Modal service error"

            repeat(MAX_ATTEMPTS) { attempt ->
                try {
                    return@withContext Result.Success(
                        generateOnce(
                            context = context,
                            text = cleanText,
                            referenceAudioBase64 = referenceBase64,
                            styleControl = styleControl,
                            cfgValue = cfgValue,
                            inferenceTimesteps = inferenceTimesteps,
                            seed = seed,
                        )
                    )
                } catch (e: ApiException) {
                    lastError = e.message ?: "Modal HTTP ${e.code}"
                    if (!e.retryable || attempt + 1 >= MAX_ATTEMPTS) {
                        return@withContext Result.Error(lastError)
                    }
                    delay(2000L * (attempt + 1))
                } catch (e: IOException) {
                    return@withContext Result.Error(
                        "Modal network timeout ဖြစ်ပါသည်။ Request ပြီးမပြီး မသေချာသောကြောင့် duplicate POST မပို့ဘဲ ပြန်စစ်ပါ: ${e.message ?: "network error"}"
                    )
                } catch (e: Exception) {
                    lastError = e.message ?: e.javaClass.simpleName
                    return@withContext Result.Error("VoxCPM2: $lastError")
                }
            }
            Result.Error("Modal VoxCPM2 မရနိုင်သေးပါ။ ${MAX_ATTEMPTS} ကြိမ် ပြန်ကြိုးစားပြီးနောက်: $lastError")
        } catch (e: Exception) {
            Result.Error("VoxCPM2 reference audio ပြင်ဆင်မရပါ: ${e.message ?: "unknown error"}")
        } finally {
            wavReference?.delete()
        }
    }

    private fun generateOnce(
        context: Context,
        text: String,
        referenceAudioBase64: String,
        styleControl: String,
        cfgValue: Float,
        inferenceTimesteps: Int,
        seed: Int?,
    ): File {
        val payload = JsonObject().apply {
            addProperty("text", text)
            addProperty("voice_mode", "clone")
            addProperty("reference_audio_base64", referenceAudioBase64)
            addProperty("style_control", styleControl)
            addProperty("cfg_value", cfgValue.coerceIn(1.0f, 4.0f))
            addProperty("inference_timesteps", inferenceTimesteps.coerceIn(4, 16))
            addProperty("output_format", "wav")
            addProperty("sample_rate", 24000)
            seed?.let { addProperty("seed", it) }
        }
        val request = Request.Builder()
            .url(CLONE_URL)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(json.toJson(payload).toRequestBody(jsonMediaType))
            .build()

        http.newCall(request).execute().use { response ->
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                val body = bytes.toString(Charsets.UTF_8)
                throw ApiException(response.code, parseError(response.code, body), isRetryable(response.code))
            }

            val audioBytes = if (contentType.contains("audio/wav") || contentType.contains("audio/x-wav")) {
                bytes
            } else {
                val body = bytes.toString(Charsets.UTF_8)
                val root = runCatching { JsonParser.parseString(body).asJsonObject }
                    .getOrElse { error("Modal response JSON မမှန်ပါ: ${body.take(MAX_ERROR_BODY)}") }
                val encoded = root.get("audio_base64")?.takeIf { !it.isJsonNull }?.asString
                    ?: error("Modal response တွင် audio_base64 မပါပါ")
                runCatching { Base64.decode(encoded, Base64.DEFAULT) }
                    .getOrElse { error("Modal audio Base64 decode မရပါ") }
            }

            if (audioBytes.size < 44 ||
                !audioBytes.copyOfRange(0, 4).contentEquals(
                    byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte())
                )) {
                error("Modal မှ WAV audio မမှန်ပါ")
            }
            val output = File.createTempFile("voxcpm2_", ".wav", context.cacheDir)
            output.writeBytes(audioBytes)
            if (!output.exists() || output.length() == 0L) {
                output.delete()
                error("Modal audio response ဗလာဖြစ်နေပါသည်")
            }
            return output
        }
    }

    /** VoxCPM2 requires WAV Base64; normalize every sample, including MP3 built-ins. */
    private fun prepareWavReference(context: Context, input: File): File {
        val output = File(context.cacheDir, "voxcpm2_ref_${System.nanoTime()}.wav")
        val command = "-y -i ${shellQuote(input.absolutePath)} -vn -ar 24000 -ac 1 -c:a pcm_s16le ${shellQuote(output.absolutePath)}"
        val session = FFmpegKit.execute(command)
        if (!ReturnCode.isSuccess(session.returnCode) || !output.exists() || output.length() < 44L) {
            output.delete()
            error("WAV reference audio သို့ ပြောင်းမရပါ")
        }
        return output
    }

    private fun shellQuote(path: String): String = "'" + path.replace("'", "'\\''") + "'"

    private fun isRetryable(code: Int): Boolean = code == 429 || code == 502 || code == 503

    private fun parseError(code: Int, body: String): String {
        val fallback = "HTTP $code: ${body.take(MAX_ERROR_BODY).ifBlank { "service error" }}"
        return runCatching {
            val root = JsonParser.parseString(body)
            if (!root.isJsonObject) return@runCatching fallback
            val obj = root.asJsonObject
            val detail = obj.get("detail")
            if (detail?.isJsonPrimitive == true) return@runCatching "HTTP $code: ${detail.asString.take(MAX_ERROR_BODY)}"
            if (detail?.isJsonObject == true) {
                val d = detail.asJsonObject
                val errorCode = d.get("code")?.asString?.takeIf { it.isNotBlank() }
                val message = d.get("message")?.asString?.takeIf { it.isNotBlank() }
                if (message != null) return@runCatching "HTTP $code${if (errorCode != null) " [$errorCode]" else ""}: ${message.take(MAX_ERROR_BODY)}"
            }
            val message = obj.get("message")?.asString?.takeIf { it.isNotBlank() }
            if (message != null) "HTTP $code: ${message.take(MAX_ERROR_BODY)}" else fallback
        }.getOrDefault(fallback)
    }

    private class ApiException(val code: Int, override val message: String, val retryable: Boolean) : Exception(message)
}

package com.recapmaker.app.media

import android.content.Context
import android.util.Base64
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
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
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Authenticated client for the project's Cloudflare VoxCPM2 relay.
 *
 * The Modal/GPU service is intentionally never called from the app. The app
 * only sends text and a WAV reference sample to the Cloudflare API.
 */
object VoxCpmClient {
    private const val BASE_URL = "https://voice.shinemovierecap.online"
    private const val GENERATE_ENDPOINT = "/api/v1/tts/generate"
    private const val MAX_ATTEMPTS = 3
    private const val MAX_ERROR_BODY = 320
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(960, TimeUnit.SECONDS)
        .callTimeout(1000, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun isConfigured(accessToken: String?): Boolean = !accessToken.isNullOrBlank()

    /**
     * Authenticated, non-generating preflight. The empty JSON body is rejected
     * with 422 after authentication, so this verifies the External API token
     * without invoking Modal inference or charging provider credits.
     *
     * @return null when authentication/service validation is acceptable; an
     * actionable Burmese error otherwise.
     */
    suspend fun preflight(accessToken: String?): String? = withContext(Dispatchers.IO) {
        if (!isConfigured(accessToken)) {
            return@withContext "Cloudflare External API token မထည့်ရသေးပါ။ Settings → VoxCPM2 API Token ထဲတွင် raw token ထည့်ပါ။"
        }
        runCatching {
            val request = Request.Builder()
                .url(BASE_URL + GENERATE_ENDPOINT)
                .header("Authorization", "Bearer ${accessToken!!.trim()}")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Idempotency-Key", "preflight-${UUID.randomUUID()}")
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 422 || response.isSuccessful) null
                else parseAuthError(response.code, body)
            }
        }.getOrElse { e -> "VoxCPM2 API သို့ ဆက်သွယ်မရပါ: ${e.message ?: "network error"}" }
    }

    suspend fun isAvailable(accessToken: String?): Boolean = preflight(accessToken) == null

    suspend fun generate(
        context: Context,
        text: String,
        referenceAudio: File,
        accessToken: String?,
        styleControl: String = "Natural pace, clear pronunciation",
        cfgValue: Float = 2.0f,
        inferenceTimesteps: Int = 10,
        seed: Int? = null,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): Result<File> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.Error("VoxCPM2 သို့ပို့ရန် စာသားမရှိပါ")
        if (!isConfigured(accessToken)) {
            return@withContext Result.Error("Cloudflare External API token မထည့်ရသေးပါ။ Settings → VoxCPM2 API Token ထဲတွင် raw token ထည့်ပါ။")
        }
        if (!referenceAudio.exists() || referenceAudio.length() == 0L) {
            return@withContext Result.Error("VoxCPM2 voice sample မတွေ့ပါ")
        }

        var wavReference: File? = null
        try {
            wavReference = prepareWavReference(context, referenceAudio)
            val referenceBase64 = Base64.encodeToString(wavReference.readBytes(), Base64.NO_WRAP)
            var lastError = "မသိရသေးသော service error"

            repeat(MAX_ATTEMPTS) { attempt ->
                try {
                    val audio = generateOnce(
                        context = context,
                        text = text,
                        referenceAudioBase64 = referenceBase64,
                        accessToken = accessToken!!,
                        styleControl = styleControl,
                        cfgValue = cfgValue,
                        inferenceTimesteps = inferenceTimesteps,
                        seed = seed,
                        idempotencyKey = idempotencyKey,
                    )
                    return@withContext Result.Success(audio)
                } catch (e: ApiException) {
                    lastError = e.message ?: "HTTP ${e.code}"
                    if (!e.retryable || attempt + 1 >= MAX_ATTEMPTS) return@withContext Result.Error(lastError)
                    delay(1500L * (attempt + 1))
                } catch (e: IOException) {
                    lastError = e.message ?: "Network error"
                    if (attempt + 1 < MAX_ATTEMPTS) delay(1500L * (attempt + 1))
                } catch (e: Exception) {
                    lastError = e.message ?: e.javaClass.simpleName
                    return@withContext Result.Error("VoxCPM2: $lastError")
                }
            }
            Result.Error("VoxCPM2 မရနိုင်သေးပါ။ ${MAX_ATTEMPTS} ကြိမ် ပြန်ကြိုးစားပြီးနောက်: $lastError")
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
        accessToken: String,
        styleControl: String,
        cfgValue: Float,
        inferenceTimesteps: Int,
        seed: Int?,
        idempotencyKey: String,
    ): File {
        val payload = JsonObject().apply {
            addProperty("text", text)
            addProperty("voice_mode", "clone")
            addProperty("reference_audio_base64", referenceAudioBase64)
            addProperty("style_control", styleControl)
            addProperty("cfg_value", cfgValue.coerceIn(1.0f, 3.0f))
            addProperty("inference_timesteps", inferenceTimesteps.coerceIn(1, 50))
            seed?.let { addProperty("seed", it) }
        }
        val request = Request.Builder()
            .url(BASE_URL + GENERATE_ENDPOINT)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .header("Idempotency-Key", idempotencyKey)
            .post(com.google.gson.Gson().toJson(payload).toRequestBody(jsonMediaType))
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(response.code, parseError(response.code, body), isRetryable(response.code))
            }
            val root = runCatching { JsonParser.parseString(body).asJsonObject }
                .getOrElse { error("VoxCPM2 response JSON မမှန်ပါ: ${body.take(MAX_ERROR_BODY)}") }
            val encoded = root.get("audio_base64")?.takeIf { !it.isJsonNull }?.asString
                ?: error("VoxCPM2 response တွင် audio_base64 မပါပါ")
            val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }
                .getOrElse { error("VoxCPM2 audio Base64 decode မရပါ") }
            if (bytes.size < 44 || !bytes.copyOfRange(0, 4).contentEquals(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))) {
                error("VoxCPM2 မှ WAV audio မမှန်ပါ")
            }
            val output = File.createTempFile("voxcpm2_", ".wav", context.cacheDir)
            output.writeBytes(bytes)
            if (!output.exists() || output.length() == 0L) {
                output.delete()
                error("VoxCPM2 audio response ဗလာဖြစ်နေပါသည်")
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

    private fun isRetryable(code: Int): Boolean = code == 429 || code == 502 || code == 503 || code == 504

    private fun parseAuthError(code: Int, body: String): String {
        val detail = parseError(code, body)
        return when (code) {
            401 -> "Cloudflare External API token မမှန်ပါ သို့မဟုတ် သက်တမ်းကုန်ပါပြီ။ Admin panel → External API tokens မှ raw token အသစ်ထည့်ပါ။ ($detail)"
            402 -> "Cloudflare VoxCPM2 credit မလုံလောက်ပါ။ ($detail)"
            422 -> "VoxCPM2 request validation မအောင်မြင်ပါ။ ($detail)"
            429 -> "VoxCPM2 rate limit/provider capacity ဖြစ်နေပါသည်။ ခဏစောင့်ပြီး ပြန်စမ်းပါ။ ($detail)"
            502, 503, 504 -> "VoxCPM2 relay service ခဏမရနိုင်ပါ။ ($detail)"
            else -> detail
        }
    }

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

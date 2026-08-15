package com.recapmaker.app.media

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.recapmaker.app.data.repository.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Direct client for the public Hugging Face VoxCPM Gradio Space. */
object VoxCpmClient {
    private const val BASE_URL = "https://openbmb-voxcpm-demo.hf.space"
    private const val GENERATE_ENDPOINT = "/gradio_api/call/generate"
    private const val MAX_TEXT_CHARS = 500
    private const val MAX_ATTEMPTS = 3
    private const val MAX_ERROR_BODY = 320
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(360, TimeUnit.SECONDS)
        .callTimeout(390, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Check that the public Space is awake and still exposes /generate. */
    suspend fun preflight(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/gradio_api/info")
                .header("Accept", "application/json")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    !response.isSuccessful -> "Hugging Face VoxCPM Space မရနိုင်ပါ: HTTP ${response.code}"
                    !body.contains("/generate") -> "Hugging Face Space တွင် generate endpoint မတွေ့ပါ"
                    else -> null
                }
            }
        }.getOrElse { e ->
            "Hugging Face VoxCPM Space သို့ ဆက်သွယ်မရပါ: ${e.message ?: "network error"}"
        }
    }

    suspend fun isAvailable(): Boolean = preflight() == null

    suspend fun generate(
        context: Context,
        text: String,
        referenceAudio: File? = null,
        controlInstruction: String = "",
        promptText: String = "",
        usePromptText: Boolean = false,
        cfgValue: Float = 2.0f,
        normalize: Boolean = true,
        denoise: Boolean = true,
    ): Result<File> = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return@withContext Result.Error("VoxCPM စာသားဗလာဖြစ်နေပါသည်")
        if (cleanText.length > MAX_TEXT_CHARS) {
            return@withContext Result.Error("Hugging Face request တစ်ကြိမ်လျှင် စာလုံး ${MAX_TEXT_CHARS} အထိသာ ပို့ပါသည်")
        }
        if (referenceAudio != null && (!referenceAudio.exists() || referenceAudio.length() == 0L)) {
            return@withContext Result.Error("VoxCPM voice sample မတွေ့ပါ")
        }

        var lastError = "မသိရသေးသော Hugging Face service error"
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val file = generateOnce(
                    context = context,
                    text = cleanText,
                    referenceAudio = referenceAudio,
                    controlInstruction = controlInstruction,
                    promptText = promptText,
                    usePromptText = usePromptText,
                    cfgValue = cfgValue,
                    normalize = normalize,
                    denoise = denoise,
                )
                return@withContext Result.Success(file)
            } catch (e: ApiException) {
                lastError = e.message ?: "HTTP ${e.code}"
                if (!e.retryable || attempt + 1 >= MAX_ATTEMPTS) return@withContext Result.Error(lastError)
                delay(1500L * (attempt + 1))
            } catch (e: IOException) {
                lastError = e.message ?: "network error"
                if (attempt + 1 < MAX_ATTEMPTS) delay(1500L * (attempt + 1))
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                if (attempt + 1 < MAX_ATTEMPTS) delay(1500L * (attempt + 1))
            }
        }
        Result.Error("Hugging Face VoxCPM မရနိုင်သေးပါ။ ${MAX_ATTEMPTS} ကြိမ် ပြန်ကြိုးစားပြီးနောက်: $lastError")
    }

    private fun generateOnce(
        context: Context,
        text: String,
        referenceAudio: File?,
        controlInstruction: String,
        promptText: String,
        usePromptText: Boolean,
        cfgValue: Float,
        normalize: Boolean,
        denoise: Boolean,
    ): File {
        val referenceData = referenceAudio?.let { uploadReference(it) }
        val data = JsonArray().apply {
            add(text)
            add(controlInstruction)
            if (referenceData == null) add(com.google.gson.JsonNull.INSTANCE) else add(referenceData)
            add(usePromptText)
            add(promptText)
            add(cfgValue.coerceIn(1.0f, 3.0f))
            add(normalize)
            add(denoise)
        }
        val payload = JsonObject().apply { add("data", data) }
        val request = Request.Builder()
            .url(BASE_URL + GENERATE_ENDPOINT)
            .header("Accept", "application/json")
            .post(gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val eventId = http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, "Hugging Face request failed (${response.code}): ${body.take(MAX_ERROR_BODY)}", response.code >= 500 || response.code == 429)
            runCatching { JsonParser.parseString(body).asJsonObject.get("event_id")?.asString }.getOrNull()
                ?: error("Hugging Face job id မရပါ: ${body.take(MAX_ERROR_BODY)}")
        }
        val result = awaitResult(eventId)
        val outputUrl = outputUrl(result) ?: error("Hugging Face job ပြီးသော်လည်း audio file မရပါ")
        val outputFile = File.createTempFile("voxcpm_", ".mp3", context.cacheDir)
        download(outputUrl, outputFile)
        if (!outputFile.exists() || outputFile.length() == 0L) {
            outputFile.delete()
            error("Hugging Face audio response ဗလာဖြစ်နေပါသည်")
        }
        return outputFile
    }

    private fun uploadReference(file: File): JsonObject {
        require(file.exists() && file.length() > 0L) { "reference audio is empty" }
        val mime = when (file.extension.lowercase()) {
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            else -> "audio/mpeg"
        }.toMediaType()
        val multipart = MultipartBody.Part.createFormData("files", file.name, file.asRequestBody(mime))
        val request = Request.Builder()
            .url("$BASE_URL/gradio_api/upload")
            .post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(multipart).build())
            .build()
        return http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, "reference upload failed (${response.code}): ${body.take(MAX_ERROR_BODY)}", response.code >= 500 || response.code == 429)
            val parsed = JsonParser.parseString(body)
            val first = if (parsed.isJsonArray && parsed.asJsonArray.size() > 0) parsed.asJsonArray[0] else parsed
            val path = when {
                first.isJsonPrimitive -> first.asString
                first.isJsonObject -> first.asJsonObject.get("path")?.asString ?: first.asJsonObject.get("url")?.asString
                else -> null
            } ?: error("reference upload returned no path")
            JsonObject().apply {
                addProperty("path", path)
                addProperty("orig_name", file.name)
                addProperty("size", file.length())
                addProperty("mime_type", mime.toString())
                addProperty("is_stream", false)
                addProperty("url", if (path.startsWith("http")) path else "$BASE_URL/gradio_api/file=$path")
                add("meta", JsonObject().apply { addProperty("_type", "gradio.FileData") })
            }
        }
    }

    private fun awaitResult(eventId: String): JsonElement {
        val request = Request.Builder()
            .url("$BASE_URL$GENERATE_ENDPOINT/$eventId")
            .header("Accept", "text/event-stream")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Hugging Face result stream failed (${response.code})")
            var lastData: JsonElement? = null
            response.body?.source()?.buffer()?.use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val raw = line.removePrefix("data:").trim()
                    if (raw.isBlank() || raw == "null") continue
                    val data = runCatching { JsonParser.parseString(raw) }
                        .getOrElse { error("Hugging Face event JSON မမှန်ပါ") }
                    lastData = data
                    if (data.isJsonPrimitive && data.asJsonPrimitive.isString) {
                        error("Hugging Face VoxCPM job error: ${data.asString.take(MAX_ERROR_BODY)}")
                    }
                    if (data.isJsonObject) {
                        val message = data.asJsonObject.get("error")?.takeIf { !it.isJsonNull }?.asString
                        if (!message.isNullOrBlank()) error("Hugging Face VoxCPM job error: ${message.take(MAX_ERROR_BODY)}")
                    }
                    if (data.isJsonArray && data.asJsonArray.size() > 0) {
                        val first = data.asJsonArray[0]
                        if (first.isJsonObject && (first.asJsonObject.has("path") || first.asJsonObject.has("url"))) return data
                        if (first.isJsonArray && first.asJsonArray.size() > 0 && first.asJsonArray[0].isJsonObject) {
                            val nested = first.asJsonArray[0].asJsonObject
                            if (nested.has("path") || nested.has("url")) return first
                        }
                    }
                }
            }
            error("Hugging Face stream ended without audio: ${lastData?.toString()?.take(MAX_ERROR_BODY) ?: "no event data"}")
        }
    }

    private fun outputUrl(result: JsonElement): String? {
        val fileData = when {
            result.isJsonArray && result.asJsonArray.size() > 0 -> {
                val first = result.asJsonArray[0]
                if (first.isJsonArray && first.asJsonArray.size() > 0) first.asJsonArray[0] else first
            }
            result.isJsonObject -> result
            else -> null
        } ?: return null
        if (!fileData.isJsonObject) return null
        val obj = fileData.asJsonObject
        val direct = obj.get("url")?.takeIf { !it.isJsonNull }?.asString
        if (!direct.isNullOrBlank()) return direct
        val path = obj.get("path")?.takeIf { !it.isJsonNull }?.asString ?: return null
        return if (path.startsWith("http")) path else "$BASE_URL/gradio_api/file=$path"
    }

    private fun download(url: String, output: File) {
        http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) error("Hugging Face audio download failed (${response.code})")
            val body = response.body ?: error("Hugging Face audio response was empty")
            body.byteStream().use { input -> output.outputStream().use { out -> input.copyTo(out) } }
        }
    }

    private fun isRetryable(code: Int): Boolean = code == 429 || code >= 500

    private class ApiException(val code: Int, override val message: String, val retryable: Boolean) : Exception(message)
}

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
import java.io.File
import java.util.concurrent.TimeUnit

/** Direct client for the public VoxCPM Gradio Space. */
object VoxCpmClient {
    private const val BASE_URL = "https://openbmb-voxcpm-demo.hf.space"
    private const val GENERATE_ENDPOINT = "/gradio_api/call/generate"
    private const val MAX_ATTEMPTS = 3
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(360, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$BASE_URL/gradio_api/info").get().build()
            http.newCall(request).execute().use { response ->
                response.isSuccessful && response.body?.string()?.contains("/generate") == true
            }
        }.getOrDefault(false)
    }

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
        if (text.isBlank()) return@withContext Result.Error("VoxCPM text is empty")
        var lastError = "unknown service error"
        repeat(MAX_ATTEMPTS) { index ->
            try {
                val file = generateOnce(
                    context = context,
                    text = text,
                    referenceAudio = referenceAudio,
                    controlInstruction = controlInstruction,
                    promptText = promptText,
                    usePromptText = usePromptText,
                    cfgValue = cfgValue,
                    normalize = normalize,
                    denoise = denoise,
                )
                return@withContext Result.Success(file)
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                if (index + 1 < MAX_ATTEMPTS) delay(1500L * (index + 1))
            }
        }
        Result.Error("VoxCPM မရနိုင်သေးပါ။ ${MAX_ATTEMPTS} ကြိမ် ပြန်ကြိုးစားပြီးနောက်: $lastError")
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
            .post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
            .build()
        val eventId = http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("request failed (${response.code}): ${body.take(220)}")
            runCatching { JsonParser.parseString(body).asJsonObject.get("event_id")?.asString }.getOrNull()
                ?: error("no job id returned: ${body.take(220)}")
        }
        val result = awaitResult(eventId)
        val outputUrl = outputUrl(result) ?: error("job completed without an audio file")
        val outputFile = File.createTempFile("voxcpm_", ".mp3", context.cacheDir)
        download(outputUrl, outputFile)
        if (!outputFile.exists() || outputFile.length() == 0L) {
            outputFile.delete()
            error("audio response was empty")
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
            if (!response.isSuccessful) error("reference upload failed (${response.code}): ${body.take(180)}")
            val parsed = JsonParser.parseString(body)
            val first = if (parsed.isJsonArray && parsed.asJsonArray.size() > 0) parsed.asJsonArray[0] else parsed
            val path = when {
                first.isJsonPrimitive -> first.asString
                first.isJsonObject -> first.asJsonObject.get("path")?.asString
                    ?: first.asJsonObject.get("url")?.asString
                else -> null
            } ?: error("reference upload returned no path")
            JsonObject().apply {
                addProperty("path", path)
                addProperty("orig_name", file.name)
                addProperty("size", file.length())
                addProperty("mime_type", mime.toString())
                addProperty("is_stream", false)
                addProperty("url", "$BASE_URL/gradio_api/file=$path")
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
        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            error("result stream failed (${response.code})")
        }
        var lastData: JsonElement? = null
        response.use { safeResponse ->
            safeResponse.body?.source()?.buffer()?.use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val raw = line.removePrefix("data:").trim()
                    if (raw.isBlank() || raw == "null") continue
                    val data = runCatching { JsonParser.parseString(raw) }
                        .getOrElse { error("invalid event data from VoxCPM") }
                    lastData = data
                    if (data.isJsonPrimitive && data.asJsonPrimitive.isString) {
                        error("VoxCPM job error: ${data.asString.take(240)}")
                    }
                    if (data.isJsonObject) {
                        val message = data.asJsonObject.get("error")?.takeIf { !it.isJsonNull }?.asString
                        if (!message.isNullOrBlank()) error("VoxCPM job error: ${message.take(240)}")
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
        }
        error("VoxCPM stream ended without audio: ${lastData?.toString()?.take(220) ?: "no event data"}")
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
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("audio download failed (${response.code})")
            val body = response.body ?: error("audio response was empty")
            body.byteStream().use { input -> output.outputStream().use { out -> input.copyTo(out) } }
        }
    }
}

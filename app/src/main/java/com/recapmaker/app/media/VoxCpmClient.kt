package com.recapmaker.app.media

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.recapmaker.app.data.repository.Result
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Direct client for the public VoxCPM Gradio Space.
 *
 * This client is deliberately isolated from the app backend. Coin authorization
 * must happen before calling this class; this class only performs audio generation.
 */
object VoxCpmClient {
    private const val BASE_URL = "https://openbmb-voxcpm-demo.hf.space"
    private const val GENERATE_ENDPOINT = "/gradio_api/call/generate"
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .build()

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
        try {
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
            val response = http.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.Error("VoxCPM request failed (${response.code}): ${body.take(180)}")
            val eventId = runCatching { JsonParser.parseString(body).asJsonObject.get("event_id")?.asString }.getOrNull()
                ?: return@withContext Result.Error("VoxCPM did not return a job id: ${body.take(180)}")
            val output = awaitResult(eventId)
            val outputUrl = outputUrl(output)
                ?: return@withContext Result.Error("VoxCPM returned no audio file")
            val outputFile = File.createTempFile("voxcpm_", ".mp3", context.cacheDir)
            download(outputUrl, outputFile)
            if (!outputFile.exists() || outputFile.length() == 0L) {
                outputFile.delete()
                Result.Error("VoxCPM returned an empty audio file")
            } else Result.Success(outputFile)
        } catch (e: Exception) {
            Result.Error(e.message ?: "VoxCPM generation failed")
        }
    }

    private fun uploadReference(file: File): JsonObject {
        require(file.exists() && file.length() > 0L) { "Reference audio is empty" }
        val mime = when (file.extension.lowercase()) {
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            else -> "audio/mpeg"
        }.toMediaType()
        val requestBody = file.asRequestBody(mime)
        val multipart = MultipartBody.Part.createFormData("files", file.name, requestBody)
        val request = Request.Builder()
            .url("$BASE_URL/gradio_api/upload")
            .post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(multipart).build())
            .build()
        val response = http.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) error("Reference upload failed (${response.code})")
        val parsed = JsonParser.parseString(body)
        val first = if (parsed.isJsonArray && parsed.asJsonArray.size() > 0) parsed.asJsonArray[0] else parsed
        val path = when {
            first.isJsonPrimitive -> first.asString
            first.isJsonObject -> first.asJsonObject.get("path")?.asString
                ?: first.asJsonObject.get("url")?.asString
            else -> null
        } ?: error("Reference upload returned no path")
        return JsonObject().apply {
            addProperty("path", path)
            addProperty("orig_name", file.name)
            addProperty("size", file.length())
            addProperty("mime_type", mime.toString())
            addProperty("is_stream", false)
            addProperty("url", "$BASE_URL/gradio_api/file=$path")
            add("meta", JsonObject().apply { addProperty("_type", "gradio.FileData") })
        }
    }

    private fun awaitResult(eventId: String): JsonElement {
        val request = Request.Builder()
            .url("$BASE_URL$GENERATE_ENDPOINT/$eventId")
            .header("Accept", "text/event-stream")
            .get()
            .build()
        val response = http.newCall(request).execute()
        if (!response.isSuccessful) error("VoxCPM result failed (${response.code})")
        var lastData: JsonElement? = null
        response.body?.source()?.buffer()?.use { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val raw = line.removePrefix("data:").trim()
                if (raw.isBlank() || raw == "null") continue
                val data = runCatching { JsonParser.parseString(raw) }.getOrElse { error("VoxCPM returned invalid event data") }
                lastData = data
                if (data.isJsonObject) {
                    val error = data.asJsonObject.get("error")?.takeIf { !it.isJsonNull }?.asString
                    if (!error.isNullOrBlank()) error("VoxCPM generation error: $error")
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
        return lastData ?: error("VoxCPM job returned no result")
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
        val response = http.newCall(request).execute()
        if (!response.isSuccessful) error("VoxCPM audio download failed (${response.code})")
        val body = response.body ?: error("VoxCPM audio response was empty")
        body.byteStream().use { input -> output.outputStream().use { out -> input.copyTo(out) } }
    }
}

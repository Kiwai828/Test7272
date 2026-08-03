package com.recapmaker.app.data.repository

import com.recapmaker.app.data.api.RecapApi
import com.recapmaker.app.data.model.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MainRepository @Inject constructor(private val api: RecapApi) {

    suspend fun getUserInfo(): Result<UserInfoResponse> = try {
        val r = api.getUserInfo()
        if (r.isSuccessful) Result.Success(r.body()!!) else Result.Error(extractDetail(r.errorBody()?.string() ?: "Failed"))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun getConfig(): Result<ConfigResponse> = try {
        val r = api.getConfig()
        if (r.isSuccessful) Result.Success(r.body()!!) else Result.Error("Failed")
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun dailyCheckin(): Result<CoinResponse> = try {
        val r = api.dailyCheckin()
        if (r.isSuccessful) Result.Success(r.body()!!) else Result.Error(extractDetail(r.errorBody()?.string() ?: "Failed"))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun deductCoins(amount: Int, reason: String, coinType: String = "auto"): Result<CoinResponse> = try {
        val r = api.deductCoins(DeductCoinsRequest(amount, reason, coinType))
        if (r.isSuccessful) Result.Success(r.body()!!) else Result.Error(extractDetail(r.errorBody()?.string() ?: "Failed"))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun refundCoins(amount: Int, reason: String, coinType: String = "gold"): Result<CoinResponse> = try {
        val r = api.refundCoins(RefundCoinsRequest(amount, reason, coinType))
        if (r.isSuccessful) Result.Success(r.body()!!) else Result.Error(extractDetail(r.errorBody()?.string() ?: "Failed"))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun geminiTts(text: String, voice: String): Result<TtsResponse> = try {
        val r = api.geminiTts(TtsRequest(text, voice))
        if (r.isSuccessful) Result.Success(r.body()!!) else Result.Error(extractDetail(r.errorBody()?.string() ?: "TTS failed"))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun analyzeText(text: String = "", instruction: String = "", audioBase64: String? = null): Result<AnalyzeResponse> = try {
        val r = api.analyzeText(AnalyzeRequest(text = text, system_instruction = instruction, audio_data = audioBase64))
        if (r.isSuccessful) Result.Success(r.body()!!) else Result.Error(extractDetail(r.errorBody()?.string() ?: "Failed"))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun groqStt(audioFile: File, language: String = "my"): Result<SttResponse> = try {
        val audioPart = MultipartBody.Part.createFormData("audio", audioFile.name, audioFile.asRequestBody("audio/mpeg".toMediaType()))
        val langPart = language.toRequestBody("text/plain".toMediaType())
        val modelPart = "whisper-large-v3".toRequestBody("text/plain".toMediaType())
        val r = api.groqStt(audioPart, langPart, modelPart)
        if (r.isSuccessful) Result.Success(r.body()!!) else Result.Error(extractDetail(r.errorBody()?.string() ?: "STT failed"))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun getPackages(): Result<List<CoinPackage>> = try {
        val r = api.getPackages()
        if (r.isSuccessful) Result.Success(r.body() ?: emptyList()) else Result.Error("Failed")
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun getEdgeTtsConfig(): Result<Pair<String, String>> = try {
        val r = api.getConfig()
        if (r.isSuccessful) {
            val body = r.body()!!
            val key = body.edge_tts_key ?: ""
            val region = body.edge_tts_region ?: "eastus"
            if (key.isNotBlank()) Result.Success(key to region) else Result.Error("No Edge TTS key configured")
        } else Result.Error("Failed to load config")
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun edgeTtsDirect(text: String, voice: String, apiKey: String, region: String = "eastus"): Result<File> {
        return try {
        val client = okhttp3.OkHttpClient()
        val ssml = """
            <speak version='1.0' xml:lang='my-MM'>
                <voice xml:lang='my-MM' xml:gender='Male' name='${voice}'>
                    $text
                </voice>
            </speak>
        """.trimIndent()

        val tokenReq = okhttp3.Request.Builder()
            .url("https://$region.api.cognitive.microsoft.com/sts/v1.0/issueToken")
            .addHeader("Ocp-Apim-Subscription-Key", apiKey)
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()
        val tokenResp = client.newCall(tokenReq).execute()
        if (!tokenResp.isSuccessful) return Result.Error("Token request failed: ${tokenResp.code}")
        val token = tokenResp.body?.string() ?: return Result.Error("Empty token")
        tokenResp.close()

        val ttsReq = okhttp3.Request.Builder()
            .url("https://$region.tts.speech.microsoft.com/cognitiveservices/v1")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/ssml+xml")
            .addHeader("X-Microsoft-OutputFormat", "audio-16khz-32kbitrate-mono-mp3")
            .post(ssml.toRequestBody("application/ssml+xml".toMediaType()))
            .build()
        val ttsResp = client.newCall(ttsReq).execute()
        if (!ttsResp.isSuccessful) return Result.Error("TTS failed: ${ttsResp.code} ${ttsResp.message}")
        val outFile = File.createTempFile("edge_tts_", ".mp3")
        ttsResp.body?.byteStream()?.use { input -> outFile.outputStream().use { output -> input.copyTo(output) } }
        ttsResp.close()
        if (outFile.length() > 0) Result.Success(outFile) else Result.Error("Empty audio response")
    } catch (e: Exception) { return Result.Error(e.message ?: "Edge TTS error") }
    }
}

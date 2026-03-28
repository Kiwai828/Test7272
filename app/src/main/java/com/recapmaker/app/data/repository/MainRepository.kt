package com.recapmaker.app.data.repository

import com.recapmaker.app.data.api.RecapApi
import com.recapmaker.app.data.model.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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

    suspend fun analyzeText(text: String, instruction: String = ""): Result<AnalyzeResponse> = try {
        val r = api.analyzeText(AnalyzeRequest(text, instruction))
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
}

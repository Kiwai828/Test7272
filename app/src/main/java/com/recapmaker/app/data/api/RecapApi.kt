package com.recapmaker.app.data.api

import com.recapmaker.app.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface RecapApi {
    @POST("api/register") suspend fun register(@Body req: RegisterRequest): Response<AuthResponse>
    @POST("api/login") suspend fun login(@Body req: LoginRequest): Response<AuthResponse>
    @POST("api/link-email") suspend fun linkEmail(@Body req: LinkEmailRequest): Response<MessageResponse>
    @POST("api/forgot-password") suspend fun forgotPassword(@Body req: ForgotPasswordRequest): Response<MessageResponse>
    @POST("api/reset-password") suspend fun resetPassword(@Body req: ResetPasswordRequest): Response<MessageResponse>
    @POST("api/change-password") suspend fun changePassword(@Body req: ChangePasswordRequest): Response<MessageResponse>
    @GET("api/user-info") suspend fun getUserInfo(): Response<UserInfoResponse>
    @GET("api/config") suspend fun getConfig(): Response<ConfigResponse>
    @POST("api/daily-checkin") suspend fun dailyCheckin(): Response<CoinResponse>
    @POST("api/deduct-coins") suspend fun deductCoins(@Body req: DeductCoinsRequest): Response<CoinResponse>
    @POST("api/refund-coins") suspend fun refundCoins(@Body req: RefundCoinsRequest): Response<CoinResponse>
    @POST("api/ai/tts") suspend fun geminiTts(@Body req: TtsRequest): Response<TtsResponse>
    @POST("api/ai/analyze") suspend fun analyzeText(@Body req: AnalyzeRequest): Response<AnalyzeResponse>
    @Multipart @POST("api/ai/stt") suspend fun groqStt(
        @Part audio: MultipartBody.Part,
        @Part("language") language: RequestBody,
        @Part("model") model: RequestBody,
    ): Response<SttResponse>
}

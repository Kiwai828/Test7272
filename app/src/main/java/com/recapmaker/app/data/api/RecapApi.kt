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

    // Video upload
    @Multipart @POST("upload-video") suspend fun uploadVideo(
        @Part video: MultipartBody.Part,
    ): Response<UrlDownloadResponse>

    // URL download (YouTube, TikTok, Facebook)
    @POST("download-from-url") suspend fun downloadFromUrl(
        @Body req: UrlDownloadRequest,
    ): Response<UrlDownloadResponse>

    // Process video (server-side)
    @Multipart @POST("process-video") suspend fun processVideo(
        @Part("video_filename") videoFilename: RequestBody,
        @Part("bypass_flip") bypassFlip: RequestBody,
        @Part("bypass_speed") bypassSpeed: RequestBody,
        @Part("bypass_pitch") bypassPitch: RequestBody,
        @Part("bypass_noise") bypassNoise: RequestBody,
        @Part("blur_areas") blurAreas: RequestBody,
        @Part("logo_x") logoX: RequestBody,
        @Part("logo_y") logoY: RequestBody,
        @Part("logo_w") logoW: RequestBody,
        @Part("logo_h") logoH: RequestBody,
        @Part logo: MultipartBody.Part?,
        @Part("text_watermark_text") textWatermarkText: RequestBody,
        @Part("text_watermark_position") textWatermarkPosition: RequestBody,
        @Part("text_watermark_size") textWatermarkSize: RequestBody,
        @Part("text_watermark_color") textWatermarkColor: RequestBody,
        @Part("text_watermark_scroll") textWatermarkScroll: RequestBody,
        @Part("text_watermark_box") textWatermarkBox: RequestBody,
        @Part("text_watermark_box_color") textWatermarkBoxColor: RequestBody,
        @Part("text_watermark_box_opacity") textWatermarkBoxOpacity: RequestBody,
        @Part("ai_text") aiText: RequestBody,
        @Part("voice_name") voiceName: RequestBody,
    ): Response<MessageResponse>

    // Get packages
    @GET("api/get_packages") suspend fun getPackages(): Response<List<CoinPackage>>

    // Voice preview
    @GET("voice-preview") suspend fun voicePreview(@Query("voice") voice: String): Response<okhttp3.ResponseBody>
}

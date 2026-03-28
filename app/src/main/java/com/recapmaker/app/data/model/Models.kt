package com.recapmaker.app.data.model

data class RegisterRequest(val username: String, val password: String, val email: String? = null)
data class LoginRequest(val username: String, val password: String)
data class LinkEmailRequest(val email: String)
data class ForgotPasswordRequest(val email: String)
data class ResetPasswordRequest(val email: String, val code: String, val new_password: String)
data class ChangePasswordRequest(val old_password: String, val new_password: String)
data class DeductCoinsRequest(val amount: Int, val reason: String, val coin_type: String = "auto")
data class RefundCoinsRequest(val amount: Int, val reason: String, val coin_type: String = "gold")
data class TtsRequest(val text: String, val voice: String = "Puck")
data class AnalyzeRequest(val text: String, val system_instruction: String = "")

data class AuthResponse(
    val status: String = "", val token: String? = null, val user_id: String? = null,
    val username: String? = null, val gold: Int = 0, val silver: Int = 0,
    val email_missing: Boolean = false, val detail: String? = null,
)
data class UserInfoResponse(
    val status: String = "", val username: String = "", val email: String? = null,
    val gold: Int = 0, val silver: Int = 0, val checked_in_today: Boolean = false,
    val checkin_silver: Int = 15, val pricing_tiers: List<PricingTier> = emptyList(),
    val packages: List<CoinPackage> = emptyList(), val payment_message: String = "",
)
data class ConfigResponse(
    val status: String = "", val maintenance_mode: Boolean = false,
    val pricing_tiers: List<PricingTier> = emptyList(),
    val packages: List<CoinPackage> = emptyList(), val payment_message: String = "",
)
data class CoinResponse(
    val status: String = "", val gold: Int = 0, val silver: Int = 0,
    val cost: Int = 0, val coin_type: String = "", val coins_earned: Int = 0,
    val detail: String? = null,
)
data class TtsResponse(
    val status: String = "", val audio_data: String? = null,
    val mime_type: String = "audio/mp3", val detail: String? = null,
)
data class AnalyzeResponse(val status: String = "", val text: String? = null, val detail: String? = null)
data class SttResponse(val status: String = "", val result: SttResult? = null, val detail: String? = null)
data class SttResult(val text: String = "", val segments: List<SttSegment> = emptyList())
data class SttSegment(val start: Double = 0.0, val end: Double = 0.0, val text: String = "")
data class MessageResponse(val status: String = "", val message: String? = null, val detail: String? = null)
data class PricingTier(val max_seconds: Int = 0, val cost: Int = 0)
data class CoinPackage(val name: String = "", val price: Int = 0, val gold: Int = 0, val silver: Int = 0, val description: String = "")

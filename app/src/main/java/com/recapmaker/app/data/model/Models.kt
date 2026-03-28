package com.recapmaker.app.data.model

// ── Auth ──
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

// All List/object fields nullable so a single bad field won't nuke the whole response
data class UserInfoResponse(
    val status: String = "", val username: String = "", val email: String? = null,
    val gold: Int = 0, val silver: Int = 0, val checked_in_today: Boolean = false,
    val checkin_silver: Int = 15, val pricing_tiers: List<PricingTier>? = null,
    val packages: List<CoinPackage>? = null, val payment_message: String? = null,
    val contact_username: String? = null,
)

data class ConfigResponse(
    val status: String = "", val maintenance_mode: Boolean = false,
    val pricing_tiers: List<PricingTier>? = null,
    val packages: List<CoinPackage>? = null, val payment_message: String? = null,
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

// ═══ BUG FIX #1 ═══
// MongoDB stores price as "5500Ks" (String). Old code had `val price: Int = 0`
// which made Gson fail → whole UserInfoResponse parse fails → coins show 0.
// Fix: ALL fields nullable String/Int? with safe accessor properties.
data class CoinPackage(
    val name: String? = null,
    val price: String? = null,      // "5500Ks" — String, NOT Int!
    val gold: Int? = null,
    val silver: Int? = null,
    val coins: Int? = null,         // some API versions use "coins" instead of "gold"
    val description: String? = null,
    val no_ads_days: Int? = null,
) {
    val displayName: String get() = name ?: "Package"
    val displayPrice: String get() = price ?: "—"
    val goldAmount: Int get() = gold ?: coins ?: 0
    val silverAmount: Int get() = silver ?: 0
}

// ── Video Processing ──
data class BlurArea(val x: Int = 0, val y: Int = 0, val w: Int = 0, val h: Int = 0)

// ── Voice Data (32 voices matching website) ──
enum class VoiceGender { Male, Female, Neutral }
enum class VoiceProvider { Google, Microsoft }

data class VoiceInfo(
    val name: String,
    val gender: VoiceGender,
    val label: String = name,
    val provider: VoiceProvider,
)

object VoiceData {
    val googleVoices = listOf(
        VoiceInfo("Aoede", VoiceGender.Female, provider = VoiceProvider.Google),
        VoiceInfo("Autonoe", VoiceGender.Female, provider = VoiceProvider.Google),
        VoiceInfo("Callirrhoe", VoiceGender.Female, provider = VoiceProvider.Google),
        VoiceInfo("Despina", VoiceGender.Female, provider = VoiceProvider.Google),
        VoiceInfo("Erinome", VoiceGender.Female, provider = VoiceProvider.Google),
        VoiceInfo("Kore", VoiceGender.Female, provider = VoiceProvider.Google),
        VoiceInfo("Laomedeia", VoiceGender.Female, provider = VoiceProvider.Google),
        VoiceInfo("Leda", VoiceGender.Female, provider = VoiceProvider.Google),
        VoiceInfo("Pulcherrima", VoiceGender.Female, provider = VoiceProvider.Google),
        VoiceInfo("Sulafat", VoiceGender.Female, provider = VoiceProvider.Google),
        VoiceInfo("Vindemiatrix", VoiceGender.Female, provider = VoiceProvider.Google),
        VoiceInfo("Zephyr", VoiceGender.Female, provider = VoiceProvider.Google),
        VoiceInfo("Achernar", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Achird", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Algenib", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Algieba", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Alnilam", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Charon", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Enceladus", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Fenrir", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Gacrux", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Iapetus", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Orus", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Puck", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Rasalgethi", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Sadachbia", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Sadaltager", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Schedar", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Umbriel", VoiceGender.Male, provider = VoiceProvider.Google),
        VoiceInfo("Zubenelgenubi", VoiceGender.Neutral, provider = VoiceProvider.Google),
    )

    val microsoftVoices = listOf(
        VoiceInfo("ThihaNeural", VoiceGender.Male, "Thiha (Male)", VoiceProvider.Microsoft),
        VoiceInfo("NilarNeural", VoiceGender.Female, "Nilar (Female)", VoiceProvider.Microsoft),
    )

    val allVoices = googleVoices + microsoftVoices
    val geminiVoiceNames = googleVoices.map { it.name }.toSet()
    fun isGeminiVoice(name: String) = name in geminiVoiceNames
}


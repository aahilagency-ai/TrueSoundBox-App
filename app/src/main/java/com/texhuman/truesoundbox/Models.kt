package com.texhuman.truesoundbox

data class PaymentItem(
    val id: String = "",
    val amount: String = "0",
    val payerName: String = "UPI Customer",
    val appName: String = "UPI QR",
    val timeFormatted: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class SubscriptionInfo(
    val status: String = "expired",
    val expiryMillis: Long = 0L,
    val hasUsedTrial: Boolean = false,
    val lastPaymentId: String = ""
) {
    val isUnlocked: Boolean
        get() = (status == "trial" || status == "active") && expiryMillis > System.currentTimeMillis()
}

data class VoiceLanguage(
    val code: String,
    val name: String,
    val nativeName: String
)

object SupportedLanguages {
    val list = listOf(
        VoiceLanguage("hi", "Hindi", "हिन्दी"),
        VoiceLanguage("en", "English", "English"),
        VoiceLanguage("ur", "Urdu", "اردو"),
        VoiceLanguage("bn", "Bengali", "বাংলা"),
        VoiceLanguage("mr", "Marathi", "मराठी"),
        VoiceLanguage("gu", "Gujarati", "ગુજરાતી"),
        VoiceLanguage("ta", "Tamil", "தமிழ்"),
        VoiceLanguage("te", "Telugu", "తెలుగు"),
        VoiceLanguage("kn", "Kannada", "ಕನ್ನಡ"),
        VoiceLanguage("pa", "Punjabi", "ਪੰਜਾਬੀ")
    )
}

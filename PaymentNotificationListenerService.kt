package com.texhuman.truesoundbox

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.regex.Pattern

class PaymentNotificationListenerService : NotificationListenerService() {

    private val upiPackages = setOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp",
        "com.amazon.mShop.android.shopping"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (!upiPackages.contains(pkg)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val combined = "$title $text"

        if (!combined.contains("received", ignoreCase = true) &&
            !combined.contains("paid you", ignoreCase = true) &&
            !combined.contains("credited", ignoreCase = true)
        ) return

        val amountMatcher = Pattern.compile("(?:₹|Rs\.?|INR)\s*([0-9,]+(?:\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)
            .matcher(combined)

        if (amountMatcher.find()) {
            val rawAmt = amountMatcher.group(1)?.replace(",", "") ?: return
            val payer = title.replace(Regex("paid you.*", RegexOption.IGNORE_CASE), "").trim()
                .ifBlank { "UPI Customer" }

            val appLabel = when (pkg) {
                "com.google.android.apps.nbu.paisa.user" -> "Google Pay"
                "com.phonepe.app" -> "PhonePe"
                "net.one97.paytm" -> "Paytm"
                "in.org.npci.upiapp" -> "BHIM UPI"
                else -> "UPI App"
            }

            SoundboxVoiceEngine.speakPayment(applicationContext, rawAmt, payer)

            val broadcast = Intent("com.texhuman.truesoundbox.NEW_PAYMENT").apply {
                setPackage(packageName)
                putExtra("amount", rawAmt)
                putExtra("payerName", payer)
                putExtra("appName", appLabel)
                putExtra("timestamp", System.currentTimeMillis())
            }
            sendBroadcast(broadcast)
        }
    }
}

package com.texhuman.truesoundbox

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.Locale

object SoundboxVoiceEngine {
    private var tts: TextToSpeech? = null
    private var isReady = false

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                tts?.language = Locale("hi", "IN")
                tts?.setSpeechRate(1.0f)
            }
        }
    }

    fun speakPayment(context: Context, amount: String, payerName: String, langCode: String = "hi") {
        val prefs = context.getSharedPreferences("TrueSoundBox_CleanPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("muted", false)) return

        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 95)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
        } catch (_: Exception) {}

        val cleanPayer = payerName.ifBlank { "Customer" }
        val text = when (langCode) {
            "en" -> "Received rupees $amount from $cleanPayer on TrueSoundBox. Thank you."
            else -> "$cleanPayer से $amount रुपये प्राप्त हुए। धन्यवाद।"
        }

        if (isReady && tts != null) {
            tts?.language = if (langCode == "en") Locale.US else Locale("hi", "IN")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "PAY_${System.currentTimeMillis()}")
        } else {
            init(context)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}

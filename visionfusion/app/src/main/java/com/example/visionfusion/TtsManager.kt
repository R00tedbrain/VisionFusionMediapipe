package com.example.visionfusion

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    private var isReady = false

    init {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        isReady = (status == TextToSpeech.SUCCESS)
        if (isReady) {
            // Configura el idioma (español)
            val localeEs = Locale("es", "ES")
            val result = textToSpeech?.setLanguage(localeEs)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsManager", "Idioma no soportado")
            }
        }
    }

    fun speak(text: String) {
        if (isReady) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun shutDown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}

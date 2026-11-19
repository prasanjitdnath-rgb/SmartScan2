package com.smartscan.app

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

class SpeechHelper(ctx: Context) {
    private var ready = false
    private lateinit var tts: TextToSpeech
    var currentKey by mutableStateOf<String?>(null)
        private set

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts.setLanguage(Locale.getDefault())
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US)
                }
                ready = true
            }
        }
    }

    /** Returns false when there is nothing to speak or the engine is not ready. */
    fun toggle(key: String, text: String): Boolean {
        if (currentKey == key) { stop(); return true }
        if (!ready || text.isBlank()) return false
        tts.stop()
        currentKey = key
        val chunks = text.chunked(3000)
        val last = "$key#${chunks.size - 1}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { }
            override fun onDone(utteranceId: String?) { if (utteranceId == last) currentKey = null }
            override fun onError(utteranceId: String?) { currentKey = null }
        })
        chunks.forEachIndexed { i, c -> tts.speak(c, TextToSpeech.QUEUE_ADD, null, "$key#$i") }
        return true
    }

    fun stop() {
        tts.stop()
        currentKey = null
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}

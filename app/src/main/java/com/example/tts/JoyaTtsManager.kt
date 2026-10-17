package com.example.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Robust Native Android Text-To-Speech Manager for Jarvis Assistant.
 * Handles asynchronous initialization, queues pending speech requests,
 * and guarantees clear, fast Hindi/English voice replies.
 */
class JoyaTtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "JarvisTtsManager"
        private const val PREFS_NAME = "JoyaTtsPrefs"
        private const val KEY_SPEECH_RATE = "tts_speech_rate"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingTexts = mutableListOf<String>()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speechRate = MutableStateFlow(prefs.getFloat(KEY_SPEECH_RATE, 1.20f))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private var lastSpokenText: String = ""

    init {
        mainHandler.post {
            tts = TextToSpeech(context, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            try {
                // Try Hindi (India), fallback to default
                var result = tts?.setLanguage(Locale("hi", "IN"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = tts?.setLanguage(Locale.forLanguageTag("hi-IN"))
                }
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.getDefault()
                }

                tts?.setSpeechRate(_speechRate.value)
                applyVoiceTone(_voiceTone.value)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })

                Log.i(TAG, "TTS initialized successfully.")

                // Speak any pending texts queued during initialization
                synchronized(pendingTexts) {
                    for (text in pendingTexts) {
                        speakInternal(text)
                    }
                    pendingTexts.clear()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error configuring TTS onInit", e)
            }
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
        }
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (text.isBlank()) return
        lastSpokenText = text

        mainHandler.post {
            if (!isInitialized || tts == null) {
                Log.w(TAG, "TTS not ready yet. Queuing text: '$text'")
                synchronized(pendingTexts) {
                    pendingTexts.add(text)
                }
                return@post
            }
            speakInternal(text, queueMode)
        }
    }

    private fun speakInternal(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        try {
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "jarvis_speech_${System.currentTimeMillis()}")
            tts?.speak(text, queueMode, params, "jarvis_utterance")
            Log.i(TAG, "Spoken aloud: '$text'")
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking text", e)
        }
    }

    fun stop() {
        mainHandler.post {
            try {
                tts?.stop()
                _isSpeaking.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping TTS", e)
            }
        }
    }

    fun repeatLast() {
        if (lastSpokenText.isNotBlank()) {
            speak(lastSpokenText)
        } else {
            speak("मैंने अभी तक कुछ नहीं बोला है, बॉस।")
        }
    }

    private val _voiceTone = MutableStateFlow(prefs.getString("tts_voice_tone", "JARVIS") ?: "JARVIS")
    val voiceTone: StateFlow<String> = _voiceTone.asStateFlow()

    fun setVoiceTone(tone: String) {
        _voiceTone.value = tone
        prefs.edit().putString("tts_voice_tone", tone).apply()
        applyVoiceTone(tone)
    }

    private fun applyVoiceTone(tone: String) {
        try {
            when (tone.uppercase()) {
                "JARVIS" -> {
                    tts?.setPitch(0.72f)
                    tts?.setSpeechRate(1.20f)
                }
                "FEMALE" -> {
                    tts?.setPitch(1.20f)
                    tts?.setSpeechRate(1.25f)
                }
                "MALE" -> {
                    tts?.setPitch(0.85f)
                    tts?.setSpeechRate(1.20f)
                }
                else -> {
                    tts?.setPitch(1.0f)
                    tts?.setSpeechRate(_speechRate.value)
                }
            }
        } catch (e: Exception) {}
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        _speechRate.value = clamped
        prefs.edit().putFloat(KEY_SPEECH_RATE, clamped).apply()
        try {
            tts?.setSpeechRate(clamped)
        } catch (e: Exception) {}
    }

    fun shutdown() {
        mainHandler.post {
            try {
                tts?.stop()
                tts?.shutdown()
                tts = null
                isInitialized = false
            } catch (e: Exception) {
                Log.e(TAG, "Error shutting down TTS", e)
            }
        }
    }
}

package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * High-speed, on-device Android SpeechRecognizer listener for Jarvis.
 * Keeps SpeechRecognizer warm and reusable across commands (no destroy/create spam).
 * Evaluates partial results with 500ms silence timeout for instant reaction.
 */
class JoyaFastVoiceCommander(
    private val context: Context,
    private val onCommandRecognized: (String, Long) -> Unit, // passes command and timestamp (for latency HUD)
    private val onListeningStateChanged: (Boolean) -> Unit = {}
) {
    companion object {
        private const val TAG = "JarvisFastVoice"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var lastExecutedCommandTime = 0L
    private var recognitionStartTime = 0L

    init {
        mainHandler.post {
            initRecognizerOnce()
        }
    }

    private fun initRecognizerOnce() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "SpeechRecognizer is not available!")
            return
        }
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {}

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createListener())
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            onListeningStateChanged(true)
            Log.d(TAG, "SpeechRecognizer ready.")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech started.")
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "Speech ended.")
        }

        override fun onError(error: Int) {
            isListening = false
            onListeningStateChanged(false)
            Log.w(TAG, "SpeechRecognizer error: $error, restarting listening...")
            // Re-arm automatically
            mainHandler.postDelayed({
                startListening()
            }, 300L)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            onListeningStateChanged(false)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val best = matches[0]
                val latency = System.currentTimeMillis() - recognitionStartTime
                Log.i(TAG, "Final result: '$best' (latency: ${latency}ms)")
                checkAndDispatch(best, isFinal = true, latency = latency)
            }
            // Auto re-arm for continuous background listening
            mainHandler.postDelayed({
                startListening()
            }, 200L)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!partials.isNullOrEmpty()) {
                val text = partials[0]
                val latency = System.currentTimeMillis() - recognitionStartTime
                Log.d(TAG, "Partial hypothesis: '$text' (${latency}ms)")
                if (checkAndDispatch(text, isFinal = false, latency = latency)) {
                    try {
                        speechRecognizer?.stopListening()
                    } catch (e: Exception) {}
                    isListening = false
                    onListeningStateChanged(false)
                    // Auto re-arm
                    mainHandler.postDelayed({
                        startListening()
                    }, 300L)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun checkAndDispatch(text: String, isFinal: Boolean, latency: Long): Boolean {
        val lower = text.lowercase().trim()
        val now = System.currentTimeMillis()
        if (now - lastExecutedCommandTime < 400L) return false

        // Fast local triggers
        val triggers = listOf(
            "youtube", "यूट्यूब", "instagram", "इंस्टाग्राम", "whatsapp", "व्हाट्सएप",
            "google", "गूगल", "search", "सर्च", "camera", "कैमरा", "video", "वीडियो",
            "gallery", "गैलरी", "torch", "flashlight", "टॉर्च", "volume", "आवाज", "aawaz",
            "music", "gana", "gaana", "गाना", "play", "प्ले", "pause", "agla", "pichla",
            "scroll", "like", "back", "home", "screenshot", "screen", "call", "कॉल", "phone",
            "alarm", "अलार्म", "timer", "टाइमर", "weather", "मौसम", "website", "दुकान", "dukaan"
        )

        val hasTrigger = triggers.any { lower.contains(it) }

        if (hasTrigger || isFinal) {
            Log.i(TAG, "DISPATCH COMMAND: '$text' (isFinal=$isFinal, latency=${latency}ms)")
            lastExecutedCommandTime = now
            onCommandRecognized(text, latency)
            return true
        }
        return false
    }

    fun startListening() {
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initRecognizerOnce()
                }
                recognitionStartTime = System.currentTimeMillis()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 200L)
                }
                speechRecognizer?.startListening(intent)
                isListening = true
                onListeningStateChanged(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting SpeechRecognizer", e)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                isListening = false
                onListeningStateChanged(false)
            } catch (e: Exception) {}
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {}
        }
    }
}

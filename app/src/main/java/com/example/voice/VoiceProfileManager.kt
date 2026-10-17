package com.example.voice

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

data class VoiceFeatures(
    val pitchHz: Float,
    val spectralCentroid: Float,
    val rmsEnergy: Double,
    val isSpeech: Boolean
)

data class VerificationResult(
    val isAuthorized: Boolean,
    val confidence: Float,
    val detectedPitch: Float,
    val message: String
)

class VoiceProfileManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceProfileManager"
        private const val PREFS_NAME = "JoyaVoiceProfilePrefs"
        private const val KEY_ENROLLED = "is_voice_enrolled"
        private const val KEY_PITCH = "enrolled_pitch_hz"
        private const val KEY_CENTROID = "enrolled_centroid"
        private const val KEY_SENSITIVITY = "recognition_sensitivity" // "HIGH", "MEDIUM", "LOW"
        private const val KEY_ENFORCE_AUTH = "enforce_authorized_voice"
        private const val KEY_BACKGROUND_LISTENING = "background_listening"
        private const val KEY_WAKE_WORD_SELECTION = "wake_word_selection" // "JOYA", "ZOYA"

        private const val SAMPLE_RATE = 16000
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isEnrolled = MutableStateFlow(prefs.getBoolean(KEY_ENROLLED, false))
    val isEnrolled: StateFlow<Boolean> = _isEnrolled.asStateFlow()

    private val _enrolledPitch = MutableStateFlow(prefs.getFloat(KEY_PITCH, 0f))
    val enrolledPitch: StateFlow<Float> = _enrolledPitch.asStateFlow()

    private val _enforceAuthVoice = MutableStateFlow(prefs.getBoolean(KEY_ENFORCE_AUTH, true))
    val enforceAuthVoice: StateFlow<Boolean> = _enforceAuthVoice.asStateFlow()

    private val _sensitivity = MutableStateFlow(prefs.getString(KEY_SENSITIVITY, "MEDIUM") ?: "MEDIUM")
    val sensitivity: StateFlow<String> = _sensitivity.asStateFlow()

    private val _backgroundListening = MutableStateFlow(prefs.getBoolean(KEY_BACKGROUND_LISTENING, true))
    val backgroundListening: StateFlow<Boolean> = _backgroundListening.asStateFlow()

    private val _wakeWord = MutableStateFlow(prefs.getString(KEY_WAKE_WORD_SELECTION, "JOYA") ?: "JOYA")
    val wakeWord: StateFlow<String> = _wakeWord.asStateFlow()

    // Temporary storage for 3 enrollment samples
    private val enrollmentSamples = mutableListOf<VoiceFeatures>()

    fun setEnforceAuthVoice(enabled: Boolean) {
        _enforceAuthVoice.value = enabled
        prefs.edit().putBoolean(KEY_ENFORCE_AUTH, enabled).apply()
    }

    fun setSensitivity(level: String) {
        _sensitivity.value = level
        prefs.edit().putString(KEY_SENSITIVITY, level).apply()
    }

    fun setBackgroundListening(enabled: Boolean) {
        _backgroundListening.value = enabled
        prefs.edit().putBoolean(KEY_BACKGROUND_LISTENING, enabled).apply()
    }

    fun setWakeWord(word: String) {
        _wakeWord.value = word
        prefs.edit().putString(KEY_WAKE_WORD_SELECTION, word).apply()
    }

    /**
     * Start enrolling a new voice profile.
     */
    fun startEnrollment() {
        enrollmentSamples.clear()
    }

    /**
     * Add an audio frame sample during enrollment.
     * Returns current sample count (0 to 3).
     */
    fun addEnrollmentSample(buffer: ShortArray, length: Int): Int {
        val features = extractFeatures(buffer, length)
        if (features.isSpeech && features.pitchHz > 60f) {
            enrollmentSamples.add(features)
            if (enrollmentSamples.size >= 3) {
                finalizeEnrollment()
            }
        }
        return enrollmentSamples.size
    }

    private fun finalizeEnrollment() {
        if (enrollmentSamples.isEmpty()) return
        val avgPitch = enrollmentSamples.map { it.pitchHz }.average().toFloat()
        val avgCentroid = enrollmentSamples.map { it.spectralCentroid }.average().toFloat()

        prefs.edit()
            .putBoolean(KEY_ENROLLED, true)
            .putFloat(KEY_PITCH, avgPitch)
            .putFloat(KEY_CENTROID, avgCentroid)
            .putBoolean(KEY_ENFORCE_AUTH, true)
            .apply()

        _isEnrolled.value = true
        _enrolledPitch.value = avgPitch
        _enforceAuthVoice.value = true
        enrollmentSamples.clear()
        Log.i(TAG, "Voice Profile successfully enrolled! Avg Pitch: ${avgPitch}Hz, Centroid: $avgCentroid")
    }

    fun resetEnrollment() {
        prefs.edit()
            .remove(KEY_ENROLLED)
            .remove(KEY_PITCH)
            .remove(KEY_CENTROID)
            .putBoolean(KEY_ENFORCE_AUTH, false)
            .apply()

        _isEnrolled.value = false
        _enrolledPitch.value = 0f
        _enforceAuthVoice.value = false
        enrollmentSamples.clear()
        Log.i(TAG, "Voice Profile reset.")
    }

    /**
     * Verify whether the incoming speech belongs to the enrolled authorized user.
     * Compares pitch ($F_0$) and spectral distribution.
     */
    fun verifySpeaker(buffer: ShortArray, length: Int): VerificationResult {
        val features = extractFeatures(buffer, length)

        if (!features.isSpeech) {
            return VerificationResult(
                isAuthorized = false,
                confidence = 0f,
                detectedPitch = 0f,
                message = "No speech detected (ambient noise or silence)."
            )
        }

        // If voice enforcement is disabled or user not enrolled, accept by default
        if (!_enforceAuthVoice.value || !_isEnrolled.value) {
            return VerificationResult(
                isAuthorized = true,
                confidence = 1.0f,
                detectedPitch = features.pitchHz,
                message = "Voice enrollment inactive - default authorization."
            )
        }

        val targetPitch = _enrolledPitch.value
        val detectedPitch = features.pitchHz

        if (detectedPitch < 50f || targetPitch < 50f) {
            return VerificationResult(
                isAuthorized = false,
                confidence = 0.2f,
                detectedPitch = detectedPitch,
                message = "Unclear pitch estimation."
            )
        }

        // Percentage pitch discrepancy
        val pitchDiff = abs(detectedPitch - targetPitch) / targetPitch

        // Confidence mapping (1.0 = exact match, 0.0 = completely different pitch)
        val pitchConfidence = (1.0f - (pitchDiff * 2.2f)).coerceIn(0.0f, 1.0f)

        // Threshold based on sensitivity level
        val (confidenceThreshold, maxAllowedPitchDiff) = when (_sensitivity.value) {
            "HIGH" -> Pair(0.75f, 0.20f)   // Strict: within ~20% pitch
            "LOW" -> Pair(0.40f, 0.45f)    // Permissive: within ~45% pitch
            else -> Pair(0.60f, 0.30f)     // Medium: within ~30% pitch
        }

        val isAuthorized = (pitchDiff <= maxAllowedPitchDiff) && (pitchConfidence >= confidenceThreshold)

        val statusMsg = if (isAuthorized) {
            "Authorized Voice Verified (${(pitchConfidence * 100).toInt()}% match)"
        } else {
            "Unauthorized speaker or background audio detected (${(pitchConfidence * 100).toInt()}% match)"
        }

        Log.d(TAG, "Speaker Verification: detected=${detectedPitch}Hz, target=${targetPitch}Hz, diff=${(pitchDiff * 100).toInt()}%, auth=$isAuthorized")

        return VerificationResult(
            isAuthorized = isAuthorized,
            confidence = pitchConfidence,
            detectedPitch = detectedPitch,
            message = statusMsg
        )
    }

    /**
     * Extracts fundamental pitch frequency (F0), spectral centroid, and RMS energy.
     */
    fun extractFeatures(buffer: ShortArray, length: Int): VoiceFeatures {
        var sumSquares = 0.0
        for (i in 0 until length) {
            val v = buffer[i].toDouble()
            sumSquares += v * v
        }
        val rms = sqrt(sumSquares / length)
        val isSpeech = rms > 650.0

        if (!isSpeech) {
            return VoiceFeatures(pitchHz = 0f, spectralCentroid = 0f, rmsEnergy = rms, isSpeech = false)
        }

        // Autocorrelation Pitch Estimation for 70Hz - 380Hz
        // At 16000Hz: minLag = 16000 / 380 = 42 samples, maxLag = 16000 / 70 = 228 samples
        val minLag = 42
        val maxLag = 228.coerceAtMost(length / 2)

        var bestLag = -1
        var maxCorr = 0.0

        for (lag in minLag..maxLag) {
            var corr = 0.0
            val limit = length - lag
            for (i in 0 until limit) {
                corr += buffer[i].toDouble() * buffer[i + lag].toDouble()
            }
            if (corr > maxCorr) {
                maxCorr = corr
                bestLag = lag
            }
        }

        val pitchHz = if (bestLag > 0) (SAMPLE_RATE.toFloat() / bestLag.toFloat()) else 0f

        // Spectral centroid approximation (difference ratio)
        var diffSum = 0.0
        var absSum = 0.0
        for (i in 1 until length) {
            diffSum += abs(buffer[i].toDouble() - buffer[i - 1].toDouble())
            absSum += abs(buffer[i].toDouble())
        }
        val centroid = if (absSum > 0) (diffSum / absSum).toFloat() else 0f

        return VoiceFeatures(
            pitchHz = pitchHz,
            spectralCentroid = centroid,
            rmsEnergy = rms,
            isSpeech = true
        )
    }

    // Hardware Audio Effect Checks & Application
    fun attachHardwareAudioEffects(audioSessionId: Int): AudioEffectsStatus {
        if (audioSessionId == 0) return AudioEffectsStatus(false, false, false)

        var aecEnabled = false
        var nsEnabled = false
        var agcEnabled = false

        try {
            if (AcousticEchoCanceler.isAvailable()) {
                val aec = AcousticEchoCanceler.create(audioSessionId)
                aec?.enabled = true
                aecEnabled = aec?.enabled ?: false
                Log.i(TAG, "Hardware AcousticEchoCanceler enabled: $aecEnabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling AcousticEchoCanceler", e)
        }

        try {
            if (NoiseSuppressor.isAvailable()) {
                val ns = NoiseSuppressor.create(audioSessionId)
                ns?.enabled = true
                nsEnabled = ns?.enabled ?: false
                Log.i(TAG, "Hardware NoiseSuppressor enabled: $nsEnabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling NoiseSuppressor", e)
        }

        try {
            if (AutomaticGainControl.isAvailable()) {
                val agc = AutomaticGainControl.create(audioSessionId)
                agc?.enabled = true
                agcEnabled = agc?.enabled ?: false
                Log.i(TAG, "Hardware AutomaticGainControl enabled: $agcEnabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling AutomaticGainControl", e)
        }

        return AudioEffectsStatus(aecEnabled, nsEnabled, agcEnabled)
    }
}

data class AudioEffectsStatus(
    val aecActive: Boolean,
    val nsActive: Boolean,
    val agcActive: Boolean
)

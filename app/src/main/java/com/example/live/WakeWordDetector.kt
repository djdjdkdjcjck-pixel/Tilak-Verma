package com.example.live

import android.util.Log
import kotlin.math.sqrt

/**
 * Lightweight, on-device acoustic wake-word detector for "Zoya" / "Joya" / "Jawan".
 *
 * Analyzes streaming 16kHz PCM-16 audio frames without heavy ML models.
 * Uses energy envelope, zero-crossing rate (ZCR), and two-syllable phonetic cadence.
 */
class WakeWordDetector(
    private val onWakeWordDetected: () -> Unit
) {
    companion object {
        private const val TAG = "ZoyaWakeWord"
        private const val FRAME_SIZE = 480 // 30ms frames
        private const val MIN_TRIGGER_INTERVAL_MS = 1200L
    }

    private var lastTriggerTime = 0L
    private var isEnabled = true

    // State machine for "ZO/JO - YA/WAN" cadence
    private enum class CadenceState {
        IDLE,
        POSSIBLE_CONSONANT, // High ZCR / fricative / affricate (Z / J)
        FIRST_VOWEL,        // Lower ZCR, high energy (O / A)
        SYLLABLE_GAP,       // Brief dip between syllables
        SECOND_VOWEL        // Second vowel / nasal burst (YA / WAN)
    }

    private var cadenceState = CadenceState.IDLE
    private var stateStartTime = 0L

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (!enabled) {
            cadenceState = CadenceState.IDLE
        }
    }

    fun isEnabled(): Boolean = isEnabled

    /**
     * Process raw PCM-16 audio buffer.
     * Computes RMS energy and Zero-Crossing Rate to identify the two-syllable cadence.
     */
    fun processAudio(buffer: ShortArray, length: Int) {
        if (!isEnabled) return

        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < MIN_TRIGGER_INTERVAL_MS) return

        var index = 0
        while (index + FRAME_SIZE <= length) {
            var sumSquares = 0.0
            var zeroCrossings = 0
            var prevSign = buffer[index] >= 0

            for (i in index until index + FRAME_SIZE) {
                val sample = buffer[i]
                sumSquares += (sample.toDouble() * sample.toDouble())
                val currentSign = sample >= 0
                if (currentSign != prevSign) {
                    zeroCrossings++
                    prevSign = currentSign
                }
            }

            val rms = sqrt(sumSquares / FRAME_SIZE)
            val zcr = zeroCrossings.toDouble() / FRAME_SIZE

            analyzeFrame(rms, zcr, now)
            index += FRAME_SIZE
        }
    }

    private fun analyzeFrame(rms: Double, zcr: Double, now: Long) {
        val stateDuration = now - stateStartTime

        // Reset if stuck in a state too long
        if (cadenceState != CadenceState.IDLE && stateDuration > 950) {
            cadenceState = CadenceState.IDLE
        }

        // Voice activity thresholds
        val isSpeechEnergy = rms > 700.0
        val isLoudEnergy = rms > 1400.0
        val isConsonant = zcr > 0.28 && rms > 450.0 // /z/, /j/, /s/
        val isVowel = zcr < 0.24 && isSpeechEnergy  // /o/, /a/

        when (cadenceState) {
            CadenceState.IDLE -> {
                if (isConsonant) {
                    cadenceState = CadenceState.POSSIBLE_CONSONANT
                    stateStartTime = now
                }
            }

            CadenceState.POSSIBLE_CONSONANT -> {
                if (isVowel && isLoudEnergy) {
                    // Transitioned from consonant to first vowel
                    cadenceState = CadenceState.FIRST_VOWEL
                    stateStartTime = now
                } else if (stateDuration > 260) {
                    cadenceState = CadenceState.IDLE
                }
            }

            CadenceState.FIRST_VOWEL -> {
                if (stateDuration in 70..420) {
                    if (!isLoudEnergy || rms < 950.0) {
                        // Dip between syllables
                        cadenceState = CadenceState.SYLLABLE_GAP
                        stateStartTime = now
                    }
                } else if (stateDuration > 450) {
                    cadenceState = CadenceState.IDLE
                }
            }

            CadenceState.SYLLABLE_GAP -> {
                if (isVowel && isSpeechEnergy && stateDuration <= 200) {
                    // Second syllable detected
                    cadenceState = CadenceState.SECOND_VOWEL
                    stateStartTime = now
                } else if (stateDuration > 250) {
                    cadenceState = CadenceState.IDLE
                }
            }

            CadenceState.SECOND_VOWEL -> {
                if (stateDuration in 50..380) {
                    triggerWakeWord(now)
                }
            }
        }
    }

    private fun triggerWakeWord(now: Long) {
        Log.i(TAG, ">>> Wake word detected! Awakening assistant! <<<")
        lastTriggerTime = now
        cadenceState = CadenceState.IDLE
        onWakeWordDetected()
    }
}

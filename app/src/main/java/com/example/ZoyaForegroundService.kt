package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.live.LiveSessionManager
import com.example.live.WakeWordDetector
import com.example.live.ZoyaState
import com.example.overlay.JoyaFloatingOverlay
import com.example.tools.ToolExecutionEngine
import com.example.voice.JoyaFastVoiceCommander
import com.example.voice.VoiceProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Foreground Service running Joya Assistant in the background with wake-word detection,
 * PCM-16 microphone streaming, hardware echo cancellation & noise suppression,
 * speaker verification, floating circular animated overlay, and ultra-fast command execution.
 */
class ZoyaForegroundService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var floatingOverlay: JoyaFloatingOverlay? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    lateinit var liveSessionManager: LiveSessionManager
        private set
    lateinit var toolEngine: ToolExecutionEngine
        private set
    lateinit var voiceProfileManager: VoiceProfileManager
        private set
    lateinit var fastVoiceCommander: JoyaFastVoiceCommander
        private set

    private lateinit var wakeWordDetector: WakeWordDetector

    private var isRecording = false
    private val audioOutputQueue = LinkedBlockingQueue<ByteArray>()
    private var isAudioPlaybackActive = false

    // Last audio chunk for speaker verification
    private var lastAudioBuffer: ShortArray? = null
    private var lastAudioLength = 0

    // Audio format specifications: 16kHz Mono PCM-16 for input, 24kHz for Gemini TTS output
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val outputSampleRate = 24000
    private val outChannelConfig = AudioFormat.CHANNEL_OUT_MONO

    companion object {
        private const val TAG = "ZoyaService"
        private const val CHANNEL_ID = "ZOYA_PERSISTENT_CHANNEL"
        private const val NOTIFICATION_ID = 1001

        private val _currentState = MutableStateFlow(ZoyaState.IDLE)
        val currentState: StateFlow<ZoyaState> = _currentState.asStateFlow()

        private val _wakeWordEnabled = MutableStateFlow(true)
        val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

        var activeService: ZoyaForegroundService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        Log.i(TAG, "ZoyaForegroundService onCreate started.")

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Joya:ContinuousListeningWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24 hours
            }
            Log.i(TAG, "Acquired partial WakeLock for 24-hour background listening.")
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock: ${e.message}")
        }

        toolEngine = ToolExecutionEngine(this)
        voiceProfileManager = VoiceProfileManager(this)

        wakeWordDetector = WakeWordDetector {
            onWakeWordTriggered()
        }
        wakeWordDetector.setEnabled(_wakeWordEnabled.value)

        val onAudioOut: (ByteArray) -> Unit = { audioData ->
            audioOutputQueue.offer(audioData)
        }

        val onInterruptOut: () -> Unit = {
            interruptPlayback()
        }

        liveSessionManager = LiveSessionManager(this, toolEngine, onAudioOut, onInterruptOut)

        fastVoiceCommander = JoyaFastVoiceCommander(
            context = this,
            onCommandRecognized = { commandText, latency ->
                Log.i(TAG, "Fast command detected: '$commandText' (latency: ${latency}ms)")

                // Speaker verification: check if voice matches enrolled profile
                val buffer = lastAudioBuffer
                val length = lastAudioLength
                if (buffer != null && length > 0 && voiceProfileManager.isEnrolled.value && voiceProfileManager.enforceAuthVoice.value) {
                    val verification = voiceProfileManager.verifySpeaker(buffer, length)
                    if (!verification.isAuthorized) {
                        Log.w(TAG, "Fast command rejected! Unauthorized speaker: ${verification.message}")
                        return@JoyaFastVoiceCommander
                    }
                    Log.i(TAG, "Authorized owner voice verified (${(verification.confidence * 100).toInt()}% match)")
                }

                liveSessionManager.executeLocalOrMultiCommand(commandText)
            },
            onListeningStateChanged = { listening ->
                if (listening) {
                    _currentState.value = ZoyaState.LISTENING
                } else if (_currentState.value == ZoyaState.LISTENING) {
                    _currentState.value = ZoyaState.IDLE
                }
            }
        )

        createNotificationChannel()
        startForegroundNotification("Jarvis Voice Assistant", "Jarvis बैकग्राउंड में सक्रिय है। बोलें 'Jarvis'...")

        floatingOverlay = JoyaFloatingOverlay(this)
        floatingOverlay?.show()

        scope.launch(Dispatchers.Main) {
            liveSessionManager.zoyaState.collect { state ->
                _currentState.value = state
                updateNotificationForState(state)
                floatingOverlay?.updateState(state)
            }
        }

        initAudioTrack()
        startMicrophoneLoop()
    }

    private fun interruptPlayback() {
        try {
            audioOutputQueue.clear()
            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error interrupting audio playback", e)
        }
    }

    private fun onWakeWordTriggered() {
        // Speaker verification: check if voice matches enrolled profile
        val buffer = lastAudioBuffer
        val length = lastAudioLength
        if (buffer != null && length > 0 && voiceProfileManager.enforceAuthVoice.value) {
            val verification = voiceProfileManager.verifySpeaker(buffer, length)
            if (!verification.isAuthorized) {
                Log.w(TAG, "Wake-word rejected! Unauthorized speaker: ${verification.message}")
                return
            }
            Log.i(TAG, "Authorized user verified with ${(verification.confidence * 100).toInt()}% confidence.")
        }

        Log.i(TAG, "Wake word triggered! Speaking 'Ji boss' and starting mic instantly.")
        triggerHaptic()
        _currentState.value = ZoyaState.LISTENING
        liveSessionManager.ttsManager.speak("Ji boss")
        fastVoiceCommander.startListening()
        liveSessionManager.startSession()
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(120)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering haptic", e)
        }
    }

    private fun initAudioTrack() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)

            val minBuf = AudioTrack.getMinBufferSize(outputSampleRate, outChannelConfig, audioFormat)
            val bufferSize = if (minBuf > 0) minBuf * 4 else 8192

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(outputSampleRate)
                        .setChannelMask(outChannelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            startAudioPlaybackLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack", e)
        }
    }

    private fun startAudioPlaybackLoop() {
        isAudioPlaybackActive = true
        scope.launch(Dispatchers.IO) {
            while (isActive && isAudioPlaybackActive) {
                try {
                    val data = audioOutputQueue.poll(50, TimeUnit.MILLISECONDS)
                    if (data != null) {
                        if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                            audioTrack?.play()
                        }
                        audioTrack?.write(data, 0, data.size)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in audio playback loop", e)
                }
            }
        }
    }

    private fun startMicrophoneLoop() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission missing for background recording.")
            return
        }

        try {
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = if (minBuf > 0) minBuf * 4 else 8192

            val ctx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                createAttributionContext("zoya_audio")
            } else {
                this
            }

            audioRecord = AudioRecord.Builder()
                .setContext(ctx)
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(audioFormat)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed!")
                return
            }

            // Attach hardware acoustic echo canceler, noise suppressor, and automatic gain control
            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                val status = voiceProfileManager.attachHardwareAudioEffects(sessionId)
                Log.i(TAG, "Audio effects configured: AEC=${status.aecActive}, NS=${status.nsActive}, AGC=${status.agcActive}")
            }

            audioRecord?.startRecording()
            isRecording = true
            Log.i(TAG, "Microphone recording started with hardware AEC and noise suppression.")

            scope.launch(Dispatchers.IO) {
                val chunkSize = 1600 // 100ms chunk at 16kHz
                val audioBuffer = ShortArray(chunkSize)

                var consecutiveSpeechFrames = 0

                while (isActive && isRecording) {
                    try {
                        val readCount = audioRecord?.read(audioBuffer, 0, chunkSize) ?: 0
                        if (readCount > 0) {
                            lastAudioBuffer = audioBuffer.clone()
                            lastAudioLength = readCount

                            processMicrophoneChunk(audioBuffer, readCount, consecutiveSpeechFrames) { speechDetected ->
                                if (speechDetected) consecutiveSpeechFrames++ else consecutiveSpeechFrames = 0
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in microphone read loop", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize microphone loop", e)
        }
    }

    private fun processMicrophoneChunk(
        buffer: ShortArray,
        length: Int,
        consecutiveSpeech: Int,
        onSpeechState: (Boolean) -> Unit
    ) {
        val state = liveSessionManager.zoyaState.value

        // 1. If IDLE, listen for wake-word "Joya" or "Zoya"
        if (state == ZoyaState.IDLE) {
            if (voiceProfileManager.backgroundListening.value) {
                wakeWordDetector.processAudio(buffer, length)
            }
            return
        }

        // 2. Compute audio energy for fast client-side interruption detection
        var sumSquares = 0.0
        for (i in 0 until length) {
            val v = buffer[i].toDouble()
            sumSquares += v * v
        }
        val rms = kotlin.math.sqrt(sumSquares / length)
        val isUserSpeaking = rms > 1600.0
        onSpeechState(isUserSpeaking)

        // 3. If Joya is speaking and user starts talking, interrupt instantly!
        if (state == ZoyaState.SPEAKING && isUserSpeaking && consecutiveSpeech >= 1) {
            Log.i(TAG, "Instant user interruption detected! Flushing audio queue.")
            interruptPlayback()
        }

        // 4. Send audio chunk to Gemini Live WebSocket
        liveSessionManager.sendAudioData(buffer, length)
    }

    fun triggerManualListening() {
        triggerHaptic()
        _currentState.value = ZoyaState.LISTENING
        fastVoiceCommander.startListening()
        liveSessionManager.startSession()
    }

    fun toggleWakeWord(enabled: Boolean) {
        _wakeWordEnabled.value = enabled
        wakeWordDetector.setEnabled(enabled)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == "START_SESSION") {
            triggerManualListening()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        activeService = null
        isRecording = false
        isAudioPlaybackActive = false
        _currentState.value = ZoyaState.IDLE
        audioOutputQueue.clear()

        try { audioRecord?.stop() } catch (e: Exception) {}
        try { audioRecord?.release() } catch (e: Exception) {}
        try { audioTrack?.stop() } catch (e: Exception) {}
        try { audioTrack?.release() } catch (e: Exception) {}
        try { fastVoiceCommander.destroy() } catch (e: Exception) {}
        try { liveSessionManager.stopSession() } catch (e: Exception) {}
        try { liveSessionManager.ttsManager.shutdown() } catch (e: Exception) {}
        try { floatingOverlay?.hide() } catch (e: Exception) {}
        floatingOverlay = null

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {}

        job.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Joya Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Joya listening for voice commands in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification(title: String, text: String) {
        val notification = buildNotification(title, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotificationForState(state: ZoyaState) {
        val (title, text) = when (state) {
            ZoyaState.IDLE -> Pair("Jarvis standby पर है", "कमांड देने के लिए 'Jarvis' बोलें।")
            ZoyaState.LISTENING -> Pair("Jarvis सुन रही है...", "कमांड बोलें (उदा. 'YouTube खोलो')")
            ZoyaState.THINKING -> Pair("Jarvis कार्य कर रही है...", "कमांड निष्पादित हो रही है।")
            ZoyaState.SPEAKING -> Pair("Jarvis बोल रही है...", "रोकने के लिए 'रुको' बोलें।")
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun buildNotification(title: String, text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

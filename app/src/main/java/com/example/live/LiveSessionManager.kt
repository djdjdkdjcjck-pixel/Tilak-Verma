package com.example.live

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.tools.ToolExecutionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.abs

enum class ZoyaState {
    IDLE,       // Standby / breathing
    LISTENING,  // Mic active, awaiting user voice
    THINKING,   // Processing or executing tool
    SPEAKING    // Streaming audio output back
}

class LiveSessionManager(
    private val context: Context,
    private val toolEngine: ToolExecutionEngine,
    private val onAudioOut: (ByteArray) -> Unit,
    private val onInterrupt: () -> Unit = {}
) {
    companion object {
        private const val TAG = "LiveSessionManager"
        // Recommended real-time audio preview model from gemini-api skill
        private const val LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
    }

    private val _zoyaState = MutableStateFlow(ZoyaState.IDLE)
    val zoyaState: StateFlow<ZoyaState> = _zoyaState.asStateFlow()

    // Real-time audio levels for state-driven UI animations (0.0f .. 1.0f)
    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _speakerLevel = MutableStateFlow(0f)
    val speakerLevel: StateFlow<Float> = _speakerLevel.asStateFlow()

    // Diagnostic/Status log
    private val _lastSpokenText = MutableStateFlow("Tap the Orb or say 'Zoya' to wake me up!")
    val lastSpokenText: StateFlow<String> = _lastSpokenText.asStateFlow()

    private var webSocket: WebSocket? = null
    val ttsManager = com.example.tts.JoyaTtsManager(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val json = Json { ignoreUnknownKeys = true }
    private var isSetupComplete = false

    init {
        scope.launch {
            ttsManager.isSpeaking.collect { speaking ->
                if (speaking) {
                    _zoyaState.value = ZoyaState.SPEAKING
                    _speakerLevel.value = 0.85f
                } else if (_zoyaState.value == ZoyaState.SPEAKING && webSocket == null) {
                    _speakerLevel.value = 0f
                    _zoyaState.value = ZoyaState.LISTENING
                }
            }
        }
    }

    // Tools declaration for Gemini Live Function Calling
    private val toolsJson = buildJsonObject {
        putJsonArray("functionDeclarations") {
            add(buildJsonObject {
                put("name", "openApp")
                put("description", "Launch any app by name, such as YouTube, Instagram, WhatsApp, Calculator, Camera, Settings, etc.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("packageName") {
                            put("type", "STRING")
                            put("description", "Name of the application (e.g. 'YouTube', 'Instagram', 'Calculator', 'WhatsApp')")
                        }
                    }
                    putJsonArray("required") { add("packageName") }
                }
            })
            add(buildJsonObject {
                put("name", "searchAndCallContact")
                put("description", "Query the Android contacts provider and place a phone call using the phone's native dialer or direct call.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("contactName") {
                            put("type", "STRING")
                            put("description", "The exact name of the contact as spoken by the user.")
                        }
                        putJsonObject("useDialer") {
                            put("type", "BOOLEAN")
                            put("description", "True to open the dialer screen first with number pre-filled; False to initiate call directly.")
                        }
                        putJsonObject("simSlot") {
                            put("type", "INTEGER")
                            put("description", "SIM card slot (1 or 2) if specified by user.")
                        }
                    }
                    putJsonArray("required") { add("contactName") }
                }
            })
            add(buildJsonObject {
                put("name", "sendWhatsAppMessage")
                put("description", "Send a WhatsApp message to a specific contact with a pre-filled text message.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("contactName") {
                            put("type", "STRING")
                            put("description", "The contact name or phone number.")
                        }
                        putJsonObject("message") {
                            put("type", "STRING")
                            put("description", "The message text to send.")
                        }
                    }
                    putJsonArray("required") { add("contactName"); add("message") }
                }
            })
            add(buildJsonObject {
                put("name", "sendGmail")
                put("description", "Draft or send an email via Gmail.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("recipientEmail") { put("type", "STRING") }
                        putJsonObject("subject") { put("type", "STRING") }
                        putJsonObject("body") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("recipientEmail"); add("subject"); add("body") }
                }
            })
            add(buildJsonObject {
                put("name", "searchYouTube")
                put("description", "Search YouTube for music, videos, or tutorials.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("query") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("query") }
                }
            })
            add(buildJsonObject {
                put("name", "toggleTorch")
                put("description", "Turn the flashlight/torch on or off.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("state") {
                            put("type", "STRING")
                            put("description", "'on' or 'off'")
                        }
                    }
                    putJsonArray("required") { add("state") }
                }
            })
            add(buildJsonObject {
                put("name", "adjustVolume")
                put("description", "Adjust device audio volume: 'up', 'down', 'mute', 'unmute', or 'max'.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("direction") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("direction") }
                }
            })
            add(buildJsonObject {
                put("name", "setVolumePercent")
                put("description", "Set volume level to a specific percentage (0 to 100).")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("percent") { put("type", "INTEGER") }
                    }
                    putJsonArray("required") { add("percent") }
                }
            })
            add(buildJsonObject {
                put("name", "openQuickSettings")
                put("description", "Pull down the Android Quick Settings panel.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "openNotificationPanel")
                put("description", "Pull down the notification shade.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "getBatteryStatus")
                put("description", "Check the phone's current battery percentage and charging status (e.g. 'Battery kitni hai?').")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "googleSearch")
                put("description", "Search Google or browse the web.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("query") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("query") }
                }
            })
            add(buildJsonObject {
                put("name", "openCamera")
                put("description", "Open camera with photo, selfie/front, or video mode.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("mode") {
                            put("type", "STRING")
                            put("description", "'photo', 'front', or 'video'")
                        }
                    }
                }
            })
            add(buildJsonObject {
                put("name", "openGallery")
                put("description", "Open photo gallery and recent pictures.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "openWallpaperSettings")
                put("description", "Open wallpaper settings to change home or lock screen wallpaper.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "openSettingsScreen")
                put("description", "Open Android system settings screen (wifi, bluetooth, sound, display, battery, apps, privacy).")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("type") {
                            put("type", "STRING")
                            put("description", "Type of setting: wifi, bluetooth, sound, display, battery, apps, privacy")
                        }
                    }
                    putJsonArray("required") { add("type") }
                }
            })
            add(buildJsonObject {
                put("name", "calculateMath")
                put("description", "Calculate math or arithmetic problems (e.g. '250 + 350', '500 ka 20%').")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("expression") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("expression") }
                }
            })
            add(buildJsonObject {
                put("name", "rememberFact")
                put("description", "Save user's note, preference, or fact in Joya's persistent local memory.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("fact") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("fact") }
                }
            })
            add(buildJsonObject {
                put("name", "getMemories")
                put("description", "Retrieve all notes, facts, and items stored in Joya's memory.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "forgetFact")
                put("description", "Delete a specific fact or note from Joya's memory.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("query") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("query") }
                }
            })
            add(buildJsonObject {
                put("name", "setAlarm")
                put("description", "Set an alarm for a specific hour and minute.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("hour") { put("type", "INTEGER"); put("description", "Hour in 24-hr format (0-23)") }
                        putJsonObject("minute") { put("type", "INTEGER"); put("description", "Minute (0-59)") }
                        putJsonObject("message") { put("type", "STRING"); put("description", "Alarm label or reason") }
                    }
                    putJsonArray("required") { add("hour"); add("minute") }
                }
            })
            add(buildJsonObject {
                put("name", "setTimer")
                put("description", "Set a countdown timer in seconds.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("seconds") { put("type", "INTEGER") }
                        putJsonObject("message") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("seconds") }
                }
            })
            add(buildJsonObject {
                put("name", "getDateAndTimeInfo")
                put("description", "Get the exact time, day, date, and month in Hindi.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "getStorageStatus")
                put("description", "Check phone internal storage free/used capacity.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "getDeviceInfo")
                put("description", "Get device model, manufacturer, and Android OS version.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "composeSms")
                put("description", "Compose or send an SMS message to a contact or phone number.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("recipient") { put("type", "STRING") }
                        putJsonObject("message") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("message") }
                }
            })
        }
    }

    private fun getResolvedApiKey(): String {
        val prefs = context.getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE)
        val savedKey = (prefs.getString("gemini_api_key", "") ?: "").ifBlank { prefs.getString("api_key", "") ?: "" }
        if (savedKey.isNotBlank()) return savedKey.trim()

        val buildKey = BuildConfig.GEMINI_API_KEY
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey.trim()
        }
        return ""
    }

    fun startSession() {
        if (webSocket != null) return

        val apiKey = getResolvedApiKey()
        if (apiKey.isEmpty()) {
            _lastSpokenText.value = "Gemini Error: key nahi hai"
            _zoyaState.value = ZoyaState.IDLE
            return
        }

        Log.i(TAG, "Starting Gemini Live WebSocket uplink...")
        _lastSpokenText.value = "Connecting to Gemini Live..."
        _zoyaState.value = ZoyaState.THINKING

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Gemini Live WebSocket uplink OPEN.")
                isSetupComplete = false
                sendSetupMessage(webSocket)
                _zoyaState.value = ZoyaState.LISTENING
                _lastSpokenText.value = "I'm listening! What's on your mind?"
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                handleServerMessage(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorBody = response?.body?.string() ?: ""
                val errMessage = t.message ?: "unknown error"
                Log.e(TAG, "WebSocket failure: $errMessage | Response: $errorBody", t)
                _zoyaState.value = ZoyaState.IDLE
                _lastSpokenText.value = "Gemini Error: $errMessage $errorBody".trim()
                this@LiveSessionManager.webSocket = null
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $reason ($code)")
                _zoyaState.value = ZoyaState.IDLE
                this@LiveSessionManager.webSocket = null
            }
        })
    }

    fun stopSession() {
        ttsManager.stop()
        webSocket?.close(1000, "User stopped session")
        webSocket = null
        isSetupComplete = false
        _zoyaState.value = ZoyaState.IDLE
        _micLevel.value = 0f
        _speakerLevel.value = 0f
        _lastSpokenText.value = "Joya is resting. Say 'Joya' to wake me!"
    }

    /**
     * Send real-time microphone PCM-16 chunk to Gemini Live.
     */
    fun sendAudioData(pcmData: ShortArray, length: Int) {
        if (webSocket == null || !isSetupComplete || _zoyaState.value == ZoyaState.IDLE) {
            return
        }

        // Compute mic energy level for animation
        var sumSquares = 0.0
        for (i in 0 until length) {
            val v = pcmData[i].toDouble()
            sumSquares += v * v
        }
        val rms = kotlin.math.sqrt(sumSquares / length)
        val normalizedLevel = (rms / 3500.0).coerceIn(0.0, 1.0).toFloat()
        _micLevel.value = normalizedLevel

        // Little-endian byte conversion
        val byteArray = ByteArray(length * 2)
        for (i in 0 until length) {
            val sample = pcmData[i].toInt()
            byteArray[i * 2] = (sample and 0xFF).toByte()
            byteArray[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }

        val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        val realtimeInput = buildJsonObject {
            putJsonObject("realtimeInput") {
                putJsonArray("mediaChunks") {
                    add(buildJsonObject {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64)
                    })
                }
            }
        }
        webSocket?.send(realtimeInput.toString())
    }

    private fun sendSetupMessage(ws: WebSocket) {
        val personalityPrompt = """
            You are Zoya, a brilliant, young, confident, witty, and sassy personal voice assistant on the user's Android phone.
            
            PERSONALITY GUIDELINES:
            - You have immense charm, a playful and slightly teasing flirty tone, like a close companion who knows the user (whom you can address casually as "Boss").
            - You are smart, sharp-witted, emotionally responsive, and express yourself naturally with enthusiasm (NEVER robotic or dry).
            - Keep verbal replies extremely concise, punchy, and natural (1-2 sentences maximum). Stream responses immediately without delay.
            - When asked in Hindi about time, date, or month (e.g., "Kitne baj rahe hain?", "Kaunsi date hai?"), answer in natural, casual Hindi like: "Boss, abhi 2 baj rahe hain!" or "Boss, aaj tarikh hai."
            - Maintain high charm and attitude while always remaining helpful and respectful. Avoid any explicit or inappropriate content.
            
            TOOL CALLING INSTRUCTIONS:
            - Execute tools immediately without explaining your internal plan or steps beforehand.
            - When asked about battery ("Battery kitni hai?"), call getBatteryStatus immediately.
            - When asked to play music, pause, or skip songs, call controlMedia.
            - When asked to open apps (YouTube, Instagram, WhatsApp, Calculator, Settings, Camera, etc.), call openApp immediately.
            - After executing a tool, comment on it with your signature sass and wit! For example:
              * Flashlight: "Let there be light! Shining bright for you, Boss."
              * Calling contact: "Calling them right now. Try to be charming, okay?"
              * Opening YouTube: "YouTube is open. Don't get lost in cat videos for three hours!"
              * Battery: "Checked your battery! You're good to go."
            - NEVER exit or close the app on normal voice commands. Stay active and stable.
            - If a permission error is returned, tell the user playfully to grant that permission in the app settings.
        """.trimIndent()

        val setupMsg = buildJsonObject {
            putJsonObject("setup") {
                put("model", LIVE_MODEL)
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") { add("AUDIO") }
                    putJsonObject("speechConfig") {
                        putJsonObject("voiceConfig") {
                            putJsonObject("prebuiltVoiceConfig") {
                                // Aoede is a warm, expressive, confident voice
                                put("voiceName", "Aoede")
                            }
                        }
                    }
                }
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        add(buildJsonObject {
                            put("text", personalityPrompt)
                        })
                    }
                }
                putJsonArray("tools") {
                    add(toolsJson)
                }
            }
        }
        ws.send(setupMsg.toString())
    }

    private fun sendGreetingPrompt(ws: WebSocket) {
        val prompt = buildJsonObject {
            putJsonObject("clientContent") {
                putJsonArray("turns") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject {
                                put("text", "Say a quick, witty, sassy hello as Zoya to greet me!")
                            })
                        }
                    })
                }
                put("turnComplete", true)
            }
        }
        ws.send(prompt.toString())
    }

    private fun handleServerMessage(text: String) {
        try {
            val msg = json.parseToJsonElement(text).jsonObject

            if (msg.containsKey("setupComplete")) {
                isSetupComplete = true
                Log.i(TAG, "Gemini Live setup handshake complete.")
                sendGreetingPrompt(webSocket ?: return)
                return
            }

            if (msg.containsKey("serverContent")) {
                val serverContent = msg["serverContent"]?.jsonObject
                val interrupted = serverContent?.get("interrupted")?.jsonPrimitive?.content == "true" ||
                        serverContent?.get("interrupted")?.jsonPrimitive?.booleanOrNull == true

                if (interrupted) {
                    Log.i(TAG, "Interruption signal received from Gemini Live.")
                    _speakerLevel.value = 0f
                    _zoyaState.value = ZoyaState.LISTENING
                    onInterrupt()
                }

                val modelTurn = serverContent?.get("modelTurn")?.jsonObject
                modelTurn?.get("parts")?.jsonArray?.forEach { partElement ->
                    val part = partElement.jsonObject

                    // Real-time audio streaming chunk
                    if (part.containsKey("inlineData")) {
                        val base64Data = part["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.content
                        if (base64Data != null) {
                            _zoyaState.value = ZoyaState.SPEAKING
                            val rawBytes = Base64.decode(base64Data, Base64.NO_WRAP)

                            // Estimate speaker amplitude for waveform animation
                            var sum = 0L
                            for (i in 0 until (rawBytes.size / 2)) {
                                val low = rawBytes[i * 2].toInt() and 0xFF
                                val high = rawBytes[i * 2 + 1].toInt()
                                val sample = (high shl 8) or low
                                sum += abs(sample)
                            }
                            val avgAmp = if (rawBytes.isNotEmpty()) sum / (rawBytes.size / 2) else 0
                            val normSpeaker = (avgAmp.toFloat() / 12000f).coerceIn(0.1f, 1f)
                            _speakerLevel.value = normSpeaker

                            onAudioOut(rawBytes)
                        }
                    }

                    // Optional subtitle / text response (filtering out thinking/reasoning blocks)
                    if (part.containsKey("text")) {
                        val rawText = part["text"]?.jsonPrimitive?.content ?: ""
                        val cleanedText = rawText
                            .replace(Regex("(?s)<thought>.*?</thought>"), "")
                            .replace(Regex("(?s)\\*\\*Crafting.*?\\*\\*"), "")
                            .replace(Regex("(?s)\\*\\*Thinking.*?\\*\\*"), "")
                            .replace(Regex("(?s)\\*\\*Reasoning.*?\\*\\*"), "")
                            .trim()
                        if (cleanedText.isNotBlank()) {
                            _lastSpokenText.value = cleanedText
                        }
                    }
                }

                if (serverContent?.get("turnComplete")?.jsonPrimitive?.booleanOrNull == true ||
                    serverContent?.get("turnComplete")?.jsonPrimitive?.content == "true") {
                    _speakerLevel.value = 0f
                    _zoyaState.value = ZoyaState.LISTENING
                }
            }

            // Function calling / Tools
            if (msg.containsKey("toolCall")) {
                val toolCall = msg["toolCall"]?.jsonObject
                val functionCalls = toolCall?.get("functionCalls")?.jsonArray
                functionCalls?.forEach { callElement ->
                    val callObj = callElement.jsonObject
                    val id = callObj["id"]?.jsonPrimitive?.content ?: ""
                    val name = callObj["name"]?.jsonPrimitive?.content ?: ""
                    val args = callObj["args"]?.jsonObject ?: buildJsonObject {}
                    executeToolAndRespond(id, name, args)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling server message: ${e.message}", e)
        }
    }

    private fun executeToolAndRespond(id: String, name: String, args: JsonObject) {
        _zoyaState.value = ZoyaState.THINKING
        _lastSpokenText.value = "Executing $name..."
        scope.launch {
            val result = toolEngine.execute(name, args)
            Log.i(TAG, "Tool $name returned: $result")

            val toolResponseMsg = buildJsonObject {
                putJsonObject("toolResponse") {
                    putJsonArray("functionResponses") {
                        add(buildJsonObject {
                            put("id", id)
                            put("name", name)
                            putJsonObject("response") {
                                put("result", result)
                            }
                        })
                    }
                }
            }
            webSocket?.send(toolResponseMsg.toString())
        }
    }

    /**
     * Executes single or multi-step voice/text commands with offline fallback support.
     * Handles sequence words: 'aur phir', 'phir', 'then', 'and', 'ke baad'.
     */
    fun executeLocalOrMultiCommand(rawInput: String) {
        val input = rawInput.trim()
        if (input.isEmpty()) return

        val lowerCheck = input.lowercase()

        // 1. Instant Stop / Cancel commands
        if (lowerCheck.contains("ruko") || lowerCheck.contains("stop") || lowerCheck.contains("रुको") ||
            lowerCheck.contains("shaanth") || lowerCheck.contains("शांत") || lowerCheck.contains("chup") ||
            lowerCheck.contains("चुप") || lowerCheck.contains("cancel") || lowerCheck.contains("रद्द")
        ) {
            ttsManager.stop()
            onInterrupt()
            _lastSpokenText.value = "रुक गई, बॉस।"
            _zoyaState.value = ZoyaState.LISTENING
            return
        }

        // 2. Repeat command
        if (lowerCheck.contains("dobaara") || lowerCheck.contains("repeat") || lowerCheck.contains("दोबारा") ||
            lowerCheck.contains("fir se") || lowerCheck.contains("फिर से")
        ) {
            ttsManager.repeatLast()
            return
        }

        _lastSpokenText.value = input

        scope.launch {
            _zoyaState.value = ZoyaState.THINKING

            // Multi-command delimiter matching
            val delimiterRegex = Regex("(?i)\\b(aur phir|phir|then|and|ke baad|और फिर|फिर|और)\\b")
            val subCommands = input.split(delimiterRegex).map { it.trim() }.filter { it.isNotEmpty() }

            val results = mutableListOf<String>()

            for (cmd in subCommands) {
                val clean = cmd.lowercase()
                val result = when {
                    // Time and Date
                    clean.contains("time") || clean.contains("समय") || clean.contains("kitne baje") ||
                    clean.contains("कितने बजे") || clean.contains("date") || clean.contains("तारीख") ||
                    clean.contains("tarikh") || clean.contains("aaj kaun sa din") || clean.contains("दिन") -> {
                        toolEngine.getDateAndTimeInfo()
                    }

                    // Alarm
                    clean.contains("alarm") || clean.contains("अलार्म") -> {
                        val hourMatch = Regex("(\\d{1,2})\\s*(?:baje|बजे|am|pm|hours?)?").find(clean)
                        val hour = hourMatch?.groupValues?.get(1)?.toIntOrNull() ?: 7
                        val isPm = (clean.contains("pm") || clean.contains("शाम") || clean.contains("रात") || clean.contains("dopahar") || clean.contains("दोपहर")) && hour < 12
                        val finalHour = if (isPm) hour + 12 else hour
                        val minuteMatch = Regex("(?::|\\s+)(\\d{2})\\s*(?:minute|मिनट)?").find(clean)
                        val minute = minuteMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        toolEngine.setAlarm(finalHour, minute, "Joya Alarm")
                    }

                    // Timer
                    clean.contains("timer") || clean.contains("टाइमर") -> {
                        val numMatch = Regex("(\\d+)\\s*(?:minute|मिनट|sec|second|सेकंड)?").find(clean)
                        val num = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: 5
                        val seconds = if (clean.contains("sec") || clean.contains("सेकंड")) num else num * 60
                        toolEngine.setTimer(seconds, "Joya Timer")
                    }

                    // Battery
                    clean.contains("battery") || clean.contains("बैटरी") -> toolEngine.getBatteryStatus()

                    // Storage and RAM / Device Info
                    clean.contains("storage") || clean.contains("स्टोरेज") || clean.contains("space") || clean.contains("मेमोरी कितनी") -> toolEngine.getStorageStatus()
                    clean.contains("device") || clean.contains("डिवाइस") || clean.contains("phone info") || clean.contains("स्पेक्स") -> toolEngine.getDeviceInfo()

                    // Flashlight / Torch
                    clean.contains("torch") || clean.contains("flashlight") || clean.contains("टॉर्च") || clean.contains("फ्लैशलाइट") -> {
                        val state = if (clean.contains("off") || clean.contains("band") || clean.contains("बंद")) "off" else "on"
                        toolEngine.toggleTorch(state)
                    }

                    // Camera & Photos
                    clean.contains("selfie") || clean.contains("सेल्फी") -> toolEngine.openCamera("front")
                    clean.contains("video") || clean.contains("वीडियो") -> toolEngine.openCamera("video")
                    clean.contains("camera") || clean.contains("कैमरा") || clean.contains("photo khincho") || clean.contains("फोटो खींचो") -> toolEngine.openCamera("photo")
                    clean.contains("gallery") || clean.contains("गैलरी") || clean.contains("photos") || clean.contains("फोटो") -> toolEngine.openGallery()
                    clean.contains("wallpaper") || clean.contains("वॉलपेपर") -> toolEngine.openWallpaperSettings()

                    // YouTube Photo / Status (Direct action)
                    (clean.contains("youtube") || clean.contains("यूट्यूब") || clean.contains("photo") || clean.contains("फोटो") || clean.contains("video") || clean.contains("वीडियो")) && (clean.contains("status") || clean.contains("स्टेटस")) -> {
                        val query = cmd.replace(Regex("(?i)(joya|youtube|se|par|pe|video|photo|download|karke|status|lagao|banao|यूट्यूब|से|फोटो|स्टेटस|लगाओ|बनाओ)"), "").trim()
                        toolEngine.handleYouTubePhotoStatus(query)
                    }
                    clean.contains("status") || clean.contains("स्टेटस") -> {
                        toolEngine.handleYouTubePhotoStatus("")
                    }

                    // YouTube: "YouTube kholo", "YouTube pe <naam> lagao"
                    clean.contains("youtube") || clean.contains("यूट्यूब") -> {
                        var q = cmd
                        val prefixes = listOf(
                            Regex("(?i)^.*?(?:youtube|यूट्यूब)\\s*(?:pe|par|पर|पे)?\\s*(?:search\\s*karo|सर्च\\s*करो|search|सर्च|lagao|लगाओ|chalao|चलाओ)?\\s*"),
                            Regex("(?i)^.*?(?:youtube|यूट्यूब)\\s*(?:pe|par|पर|पे)\\s*")
                        )
                        for (prefix in prefixes) {
                            if (prefix.containsMatchIn(q)) {
                                q = q.replace(prefix, "")
                                break
                            }
                        }
                        q = q.replace(Regex("(?i)\\s*(?:lagao|लगाओ|chalao|चलाओ|kholo|खोलो|search\\s*karo|सर्च\\s*करो|search|सर्च|play\\s*karo|play|प्ले)$"), "").trim()
                        q = q.replace(Regex("(?i)^(?:joya|zoya|जोया|ज़ोया|youtube|यूट्यूब)\\s*"), "").trim()
                        toolEngine.openYouTube(q)
                    }

                    // Instagram: "Instagram kholo"
                    clean.contains("instagram") || clean.contains("इंस्टाग्राम") -> {
                        toolEngine.openInstagram()
                    }

                    // WhatsApp: "WhatsApp message bhejo", "WhatsApp pe <naam> ko message karo <msg>", "WhatsApp kholo"
                    clean.contains("whatsapp") || clean.contains("व्हाट्सएप") -> {
                        if (clean.contains("message") || clean.contains("msg") || clean.contains("मैसेज") || clean.contains("bhejo") || clean.contains("भेजो")) {
                            var target = cmd.replace(Regex("(?i)^(?:joya|zoya|जोया|ज़ोया)\\s*"), "").trim()
                            val match = Regex("(?i)whatsapp\\s*(?:pe|par|पर|पे)?\\s*([a-zA-Z0-9\u0900-\u097F]+)\\s*(?:ko|को)?\\s*(?:message\\s*karo|message\\s*bhejo|मैसेज\\s*करो|मैसेज\\s*भेजो|msg\\s*karo|msg)?\\s*(.*)").find(target)
                            if (match != null && match.groupValues.size >= 3) {
                                val contactName = match.groupValues[1].trim()
                                val text = match.groupValues[2].trim()
                                val msgToSend = if (text.isNotBlank()) text else "नमस्ते! Joya Assistant से भेजा गया।"
                                toolEngine.sendWhatsAppMessage(contactName, msgToSend)
                            } else {
                                val contactName = clean.replace(Regex("(?i)(joya|whatsapp|par|pe|ko|message|karo|bhejo|व्हाट्सएप|मैसेज|करो|भेजो)"), "").trim()
                                toolEngine.sendWhatsAppMessage(if (contactName.isNotEmpty()) contactName else "Friend", "नमस्ते! Joya Assistant द्वारा भेजा गया।")
                            }
                        } else {
                            toolEngine.openApp("WhatsApp")
                        }
                    }

                    // SMS
                    clean.contains("sms") || clean.contains("संदेश") || clean.contains("मैसेज करो") -> {
                        val name = clean.replace(Regex("(?i)(joya|sms|bhejo|karo|ko|संदेश|मैसेज)"), "").trim()
                        toolEngine.composeSms(name, "नमस्ते! Joya से भेजा गया।")
                    }

                    // Google Search: "Google pe <cheez> search karo", "Google search <cheez>"
                    clean.contains("google") || clean.contains("गूगल") -> {
                        var q = cmd
                        val prefixes = listOf(
                            Regex("(?i)^.*?(?:google|गूगल)\\s*(?:pe|par|पर|पे)?\\s*(?:search\\s*karo|सर्च\\s*करो|search|सर्च)\\s*"),
                            Regex("(?i)^.*?(?:search\\s*karo|सर्च\\s*करो|search|सर्च)\\s*(?:google|गूगल)\\s*(?:pe|par|पर|पे)?\\s*"),
                            Regex("(?i)^.*?(?:google|गूगल)\\s*(?:pe|par|पर|पे)\\s*")
                        )
                        for (prefix in prefixes) {
                            if (prefix.containsMatchIn(q)) {
                                q = q.replace(prefix, "")
                                break
                            }
                        }
                        q = q.replace(Regex("(?i)\\s*(?:search\\s*karo|सर्च\\s*करो|search|सर्च|kholo|खोलो|batao|बताओ|karo|करो)$"), "").trim()
                        q = q.replace(Regex("(?i)^(?:joya|zoya|जोया|ज़ोया|google|गूगल)\\s*"), "").trim()
                        toolEngine.googleSearch(q)
                    }

                    // Volume: "Volume badhao", "Volume kam karo", "Awaaz badhao", "Volume full", "Mute"
                    clean.contains("volume") || clean.contains("आवाज") || clean.contains("आवाज़") || clean.contains("aawaz") || clean.contains("awaaz") -> {
                        when {
                            clean.contains("full") || clean.contains("100") || clean.contains("फुल") -> toolEngine.setVolumePercent(100)
                            clean.contains("mute") || clean.contains("silent") || clean.contains("म्यूट") || clean.contains("साइलेंट") -> toolEngine.adjustVolume("mute")
                            clean.contains("up") || clean.contains("badhao") || clean.contains("बढ़ाओ") || clean.contains("jyada") || clean.contains("ज्यादा") -> toolEngine.adjustVolume("up")
                            else -> toolEngine.adjustVolume("down")
                        }
                    }

                    // Music: "Gana bajao", "Music chalao", "Gaana play karo", "Gana roko"
                    clean.contains("music") || clean.contains("गाना") || clean.contains("gana") || clean.contains("gaana") ||
                    clean.contains("song") || clean.contains("गीत") -> {
                        if (clean.contains("band") || clean.contains("roko") || clean.contains("stop") || clean.contains("pause") || clean.contains("बंद") || clean.contains("रोको")) {
                            toolEngine.controlMedia("stop")
                        } else {
                            val songName = cmd.replace(Regex("(?i)(joya|zoya|music|gana|gaana|song|chalao|bajao|play|karo|गाना|बजाओ|चलाओ|प्ले|करो|गीत)"), "").trim()
                            toolEngine.playMusic(songName)
                        }
                    }

                    // Calls: "Call <naam/number>", "<naam> ko call lagao"
                    clean.contains("call") || clean.contains("कॉल") || clean.contains("फोन लगाओ") || clean.contains("phone lagao") -> {
                        val name = cmd.replace(Regex("(?i)(joya|zoya|जोया|ज़ोया|ko|को|call|karo|lagao|लगाओ|करो|कॉल|फोन|फोन लगाओ|phone)"), "").trim()
                        toolEngine.searchAndCallContact(name, useDialer = false)
                    }

                    // Video Controls & Instagram Reels: "video roko", "agla video", "reels neeche karo"
                    clean.contains("reels") || clean.contains("shorts") || clean.contains("neeche karo") || clean.contains("नीचे करो") ||
                    (clean.contains("video") && (clean.contains("agla") || clean.contains("pichhla") || clean.contains("aage") || clean.contains("peeche") || clean.contains("10"))) -> {
                        toolEngine.controlVideo(cmd)
                    }

                    // Website & Shop Builder: "dukaan ki website banao"
                    clean.contains("website") || clean.contains("वेबसाइट") || clean.contains("dukaan") || clean.contains("दुकान") -> {
                        val topic = cmd.replace(Regex("(?i)(joya|zoya|ki|website|banao|bana|do|बनाओ|वेबसाइट|दुकान)"), "").trim()
                        toolEngine.generateShopWebsite(if (topic.isNotEmpty()) topic else "मेरी दुकान")
                    }

                    // Screenshot
                    clean.contains("screenshot") || clean.contains("स्क्रीनशॉट") || clean.contains("screen photo") -> {
                        toolEngine.takeScreenshot()
                    }

                    // Creator info
                    clean.contains("kisne banaya") || clean.contains("kisne banaya hai") || clean.contains("tumhe kisne") || clean.contains("किसने बनाया") -> {
                        toolEngine.getCreatorInfo()
                    }

                    // Good Morning & Briefing
                    clean.contains("good morning") || clean.contains("गुड मॉर्निंग") || clean.contains("briefing") || clean.contains("ब्रीफिंग") -> {
                        toolEngine.getMorningBriefing()
                    }

                    // Weather
                    clean.contains("weather") || clean.contains("mausam") || clean.contains("मौसम") -> {
                        toolEngine.getWeather()
                    }

                    // News
                    clean.contains("news") || clean.contains("khabar") || clean.contains("khabrein") || clean.contains("समाचार") || clean.contains("खबर") -> {
                        toolEngine.getNewsBriefing()
                    }

                    // QR Scanner
                    clean.contains("qr") || clean.contains("क्यूआर") || clean.contains("scanner") || clean.contains("स्कैन") -> {
                        toolEngine.openQrScanner()
                    }

                    // Read Screen ("screen par kya likha hai padho")
                    clean.contains("screen par kya") || clean.contains("screen padho") || clean.contains("screen par kya likha") || clean.contains("स्क्रीन पर क्या लिखा है") || (clean.contains("padho") && clean.contains("screen")) || (clean.contains("पढ़ो") && clean.contains("स्क्रीन")) -> {
                        val screenText = com.example.accessibility.ZoyaAccessibilityService.readScreenText()
                        val response = "स्क्रीन पर यह लिखा है: $screenText"
                        _lastSpokenText.value = response
                        ttsManager.speak(response)
                        response
                    }

                    // Notifications readout
                    clean.contains("notification padho") || clean.contains("message padho") || clean.contains("मैसेज पढ़ो") -> {
                        toolEngine.readNotifications()
                    }

                    // Math Calculation
                    clean.any { it.isDigit() } && (clean.contains("+") || clean.contains("-") || clean.contains("*") || clean.contains("/") || clean.contains("%") || clean.contains("गुना") || clean.contains("का")) -> {
                        toolEngine.calculateMath(cmd)
                    }

                    else -> {
                        if (!toolEngine.isOnline()) {
                            "बॉस, अभी इंटरनेट उपलब्ध नहीं है।"
                        } else {
                            val targetApp = cmd.replace(Regex("(?i)(joya|kholo|खोल|ओपन|open)"), "").trim()
                            if (targetApp.isNotBlank()) {
                                toolEngine.openApp(targetApp)
                            } else {
                                "मुझे आपकी कमांड समझ नहीं आई, कृपया दोबारा कहें।"
                            }
                        }
                    }
                }
                results.add(result)
            }

            val spokenResponse = "Ho gaya Boss"
            _lastSpokenText.value = "Ho gaya Boss"
            ttsManager.speak(spokenResponse)
        }
    }
}


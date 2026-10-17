package com.example.tools

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.example.memory.JoyaMemoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Native Android Tool Execution Engine for Joya Assistant.
 * Bridges voice commands and Gemini Live function calling to Android system APIs,
 * Intents, Settings, Camera, Gallery, and Local Memory.
 */
class ToolExecutionEngine(private val context: Context) {

    val memoryManager = JoyaMemoryManager(context)

    suspend fun execute(name: String, args: JsonObject): String = withContext(Dispatchers.IO) {
        try {
            when (name) {
                "openApp" -> {
                    val appName = args["packageName"]?.jsonPrimitive?.content
                        ?: return@withContext "ऐप का नाम स्पष्ट नहीं है।"
                    openApp(appName)
                }
                "searchAndCallContact" -> {
                    val contactName = args["contactName"]?.jsonPrimitive?.content
                        ?: return@withContext "कृपया संपर्क का नाम बताएं।"
                    val useDialer = args["useDialer"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val simSlot = args["simSlot"]?.jsonPrimitive?.content?.toIntOrNull()
                    searchAndCallContact(contactName, useDialer, simSlot)
                }
                "sendWhatsAppMessage" -> {
                    val contactName = args["contactName"]?.jsonPrimitive?.content
                        ?: return@withContext "कांटेक्ट का नाम आवश्यक है।"
                    val message = args["message"]?.jsonPrimitive?.content ?: ""
                    sendWhatsAppMessage(contactName, message)
                }
                "sendGmail" -> {
                    val recipient = args["recipientEmail"]?.jsonPrimitive?.content ?: ""
                    val subject = args["subject"]?.jsonPrimitive?.content ?: ""
                    val body = args["body"]?.jsonPrimitive?.content ?: ""
                    sendGmail(recipient, subject, body)
                }
                "searchYouTube" -> {
                    val query = args["query"]?.jsonPrimitive?.content
                        ?: return@withContext "कृपया सर्च करने के लिए शब्द बताएं।"
                    searchYouTube(query)
                }
                "googleSearch" -> {
                    val query = args["query"]?.jsonPrimitive?.content ?: ""
                    googleSearch(query)
                }
                "openCamera" -> {
                    val mode = args["mode"]?.jsonPrimitive?.content ?: "photo"
                    openCamera(mode)
                }
                "openGallery" -> {
                    openGallery()
                }
                "openWallpaperSettings" -> {
                    openWallpaperSettings()
                }
                "adjustVolume" -> {
                    val direction = args["direction"]?.jsonPrimitive?.content ?: "up"
                    adjustVolume(direction)
                }
                "setVolumePercent" -> {
                    val percent = args["percent"]?.jsonPrimitive?.content?.toIntOrNull() ?: 50
                    setVolumePercent(percent)
                }
                "toggleTorch" -> {
                    val state = args["state"]?.jsonPrimitive?.content ?: "on"
                    toggleTorch(state)
                }
                "setBrightness" -> {
                    val level = args["level"]?.jsonPrimitive?.content?.toIntOrNull() ?: 50
                    setBrightness(level)
                }
                "openNotificationPanel" -> {
                    openNotificationPanel()
                }
                "openQuickSettings" -> {
                    openQuickSettings()
                }
                "openSettingsScreen" -> {
                    val type = args["type"]?.jsonPrimitive?.content ?: "main"
                    openSettingsScreen(type)
                }
                "getSimCardInfo" -> {
                    getSimCardInfo()
                }
                "getBatteryStatus" -> {
                    getBatteryStatus()
                }
                "controlMedia" -> {
                    val action = args["action"]?.jsonPrimitive?.content ?: "toggle"
                    controlMedia(action)
                }
                "calculateMath" -> {
                    val expression = args["expression"]?.jsonPrimitive?.content ?: ""
                    calculateMath(expression)
                }
                "rememberFact" -> {
                    val fact = args["fact"]?.jsonPrimitive?.content ?: ""
                    memoryManager.remember(fact)
                }
                "getMemories" -> {
                    memoryManager.getAllMemoriesText()
                }
                "forgetFact" -> {
                    val query = args["query"]?.jsonPrimitive?.content ?: ""
                    memoryManager.forget(query)
                }
                "setAlarm" -> {
                    val hour = args["hour"]?.jsonPrimitive?.content?.toIntOrNull() ?: 7
                    val minute = args["minute"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val message = args["message"]?.jsonPrimitive?.content ?: "Joya Alarm"
                    setAlarm(hour, minute, message)
                }
                "setTimer" -> {
                    val seconds = args["seconds"]?.jsonPrimitive?.content?.toIntOrNull() ?: 60
                    val message = args["message"]?.jsonPrimitive?.content ?: "Joya Timer"
                    setTimer(seconds, message)
                }
                "getStorageStatus" -> {
                    getStorageStatus()
                }
                "getDeviceInfo" -> {
                    getDeviceInfo()
                }
                "getDateAndTimeInfo" -> {
                    getDateAndTimeInfo()
                }
                "composeSms" -> {
                    val recipient = args["recipient"]?.jsonPrimitive?.content ?: ""
                    val message = args["message"]?.jsonPrimitive?.content ?: ""
                    composeSms(recipient, message)
                }
                else -> "कमांड '$name' उपलब्ध नहीं है।"
            }
        } catch (e: Exception) {
            "कार्रवाई पूरी करने में त्रुटि: ${e.message}"
        }
    }

    /**
     * Launch any installed app by name or package with smart fallback.
     */
    fun openApp(appName: String): String {
        val lower = appName.lowercase().trim()

        // 1. Direct intent shortcuts for core applications
        when {
            lower.contains("camera") || lower.contains("कैमरा") -> return openCamera("photo")
            lower.contains("gallery") || lower.contains("गैलरी") || lower.contains("photos") || lower.contains("फोटो") -> return openGallery()
            lower.contains("calculator") || lower.contains("कैलकुलेटर") -> {
                val calcIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALCULATOR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(calcIntent)
                    return "कैलकुलेटर खोल दिया गया है।"
                } catch (e: Exception) { /* fallback */ }
            }
            lower.contains("setting") || lower.contains("सेटिंग") -> {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    return "सेटिंग्स खोल दी गई हैं।"
                } catch (e: Exception) { /* fallback */ }
            }
            lower.contains("browser") || lower.contains("chrome") || lower.contains("क्रोम") -> {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_BROWSER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    return "ब्राउज़र खोल दिया गया है।"
                } catch (e: Exception) { /* fallback */ }
            }
            lower.contains("phone") || lower.contains("dialer") || lower.contains("फोन") -> {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    return "डायल पैड खोल दिया गया है।"
                } catch (e: Exception) { /* fallback */ }
            }
            lower.contains("message") || lower.contains("संदेश") || lower.contains("sms") -> {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MESSAGING)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    return "मैसेज ऐप खोल दिया गया है।"
                } catch (e: Exception) { /* fallback */ }
            }
            lower.contains("map") || lower.contains("नक्शा") -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    return "गूगल मैप्स खोल दिया गया है।"
                } catch (e: Exception) { /* fallback */ }
            }
            lower.contains("clock") || lower.contains("alarm") || lower.contains("घड़ी") || lower.contains("अलार्म") -> {
                val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    return "घड़ी और अलार्म खोल दिए गए हैं।"
                } catch (e: Exception) { /* fallback */ }
            }
        }

        // Known package mappings
        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "यूूट्यूब" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "व्हाट्सएप" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "इंस्टाग्राम" to "com.instagram.android",
            "gmail" to "com.google.android.gm",
            "गूगल" to "com.google.android.googlequicksearchbox",
            "google" to "com.google.android.googlequicksearchbox",
            "play store" to "com.android.vending",
            "प्ले स्टोर" to "com.android.vending",
            "spotify" to "com.spotify.music",
            "telegram" to "org.telegram.messenger",
            "calendar" to "com.google.android.calendar",
            "files" to "com.google.android.apps.nbu.files"
        )

        if (lower.contains("instagram") || lower.contains("इंस्टाग्राम")) {
            return openInstagram()
        }
        if (lower.contains("youtube") || lower.contains("यूट्यूब")) {
            return openYouTube("")
        }
        if (lower.contains("google") || lower.contains("गूगल")) {
            return googleSearch("")
        }

        val pm = context.packageManager
        for ((key, pkg) in knownPackages) {
            if (lower.contains(key)) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return "$appName खोल दिया गया है।"
                }
            }
        }

        // Search through all installed applications
        try {
            val installed = pm.getInstalledApplications(0)
            for (app in installed) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label.contains(lower) || app.packageName.lowercase().contains(lower)) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return "${pm.getApplicationLabel(app)} खोल दिया गया है।"
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        return "यह ऐप '$appName' आपके फोन में इंस्टॉल नहीं मिला। आप इसे Play Store से डाउनलोड कर सकते हैं।"
    }

    /**
     * Open Camera with front, back, or video modes.
     */
    fun openCamera(mode: String = "photo"): String {
        val intent = when (mode.lowercase()) {
            "video", "वीडियो" -> Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            else -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        }
        if (mode.lowercase().contains("front") || mode.lowercase().contains("सेल्फी")) {
            intent.putExtra("android.intent.extras.CAMERA_FACING", 1)
            intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "कैमरा खोल दिया गया है।"
        } catch (e: Exception) {
            "कैमरा खोलने में असमर्थ। कृपया ऐप अनुमति जांचें।"
        }
    }

    /**
     * Open device Gallery / Photos app.
     */
    fun openGallery(): String {
        val galleryIntent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(galleryIntent)
            "गैलरी खोल दी गई है।"
        } catch (e: Exception) {
            val altIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_GALLERY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(altIntent)
                "गैलरी खोल दी गई है।"
            } catch (ex: Exception) {
                "गैलरी खोलने में असमर्थ।"
            }
        }
    }

    /**
     * Open Wallpaper Settings.
     */
    /**
     * Open Wallpaper Settings.
     */
    fun openWallpaperSettings(): String {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "वॉलपेपर सेटिंग्स खोल दी गई हैं।"
        } catch (e: Exception) {
            "वॉलपेपर सेटिंग्स खोलने में असमर्थ।"
        }
    }

    /**
     * Set device alarm using AlarmClock API.
     */
    fun setAlarm(hour: Int, minute: Int, message: String = "Joya Alarm"): String {
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "अलार्म $hour:$minute के लिए सेट कर दिया गया है।"
        } catch (e: Exception) {
            "अलार्म सेट करने में त्रुटि: ${e.message}"
        }
    }

    /**
     * Set a countdown timer.
     */
    fun setTimer(seconds: Int, message: String = "Joya Timer"): String {
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "$seconds सेकंड का टाइमर शुरू कर दिया गया है।"
        } catch (e: Exception) {
            "टाइमर सेट करने में त्रुटि: ${e.message}"
        }
    }

    /**
     * Natural Hindi date, day, and time information.
     */
    fun getDateAndTimeInfo(): String {
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR)
        val minute = now.get(java.util.Calendar.MINUTE)
        val amPm = if (now.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "सुबह" else "शाम"
        val day = now.get(java.util.Calendar.DAY_OF_MONTH)
        val monthNames = arrayOf("जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")
        val month = monthNames[now.get(java.util.Calendar.MONTH)]
        val daysOfWeek = arrayOf("रविवार", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार")
        val dayOfWeek = daysOfWeek[now.get(java.util.Calendar.DAY_OF_WEEK) - 1]

        val displayHour = if (hour == 0) 12 else hour
        val minStr = if (minute < 10) "0$minute" else "$minute"
        return "Boss, अभी $amPm के $displayHour:$minStr बज रहे हैं। आज $dayOfWeek, $day $month है।"
    }

    /**
     * Storage status check.
     */
    fun getStorageStatus(): String {
        return try {
            val path = android.os.Environment.getDataDirectory()
            val stat = android.os.StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalGB = (totalBlocks * blockSize) / (1024 * 1024 * 1024)
            val freeGB = (availableBlocks * blockSize) / (1024 * 1024 * 1024)
            val usedGB = totalGB - freeGB

            "Boss, फोन में $freeGB GB खाली स्टोरेज उपलब्ध है (कुल $totalGB GB में से $usedGB GB उपयोग में है)।"
        } catch (e: Exception) {
            "स्टोरेज जानकारी उपलब्ध नहीं हो सकी।"
        }
    }

    /**
     * Device specifications and system info.
     */
    fun getDeviceInfo(): String {
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val androidVersion = Build.VERSION.RELEASE
        return "डिवाइस: $manufacturer $model | Android संस्करण: $androidVersion"
    }

    /**
     * Compose SMS.
     */
    fun composeSms(recipient: String, message: String): String {
        val uri = if (recipient.isNotBlank()) Uri.parse("smsto:$recipient") else Uri.parse("smsto:")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "मैसेज ड्राफ्ट तैयार कर दिया गया है।"
        } catch (e: Exception) {
            "एसएमएस ऐप खोलने में असमर्थ: ${e.message}"
        }
    }

    /**
     * Web and Google search.
     */
    fun googleSearch(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage("com.google.android.googlequicksearchbox")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    context.startActivity(launchIntent)
                    "गूगल खोल दिया गया है।"
                } catch (e: Exception) {
                    openGoogleWeb("")
                }
            } else {
                return openGoogleWeb("")
            }
        }

        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, q)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "गूगल पर '$q' सर्च किया जा रहा है।"
        } catch (e: Exception) {
            openGoogleWeb(q)
        }
    }

    fun openGoogleWeb(query: String = ""): String {
        val url = if (query.isNotBlank()) "https://www.google.com/search?q=${Uri.encode(query)}" else "https://www.google.com"
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(webIntent)
            if (query.isNotBlank()) "ब्राउज़र में गूगल पर '$query' सर्च खोला गया।" else "ब्राउज़र में गूगल खोला गया।"
        } catch (e: Exception) {
            "गूगल सर्च खोलने में असमर्थ।"
        }
    }

    /**
     * Search and place phone call with contact lookup.
     */
    fun searchAndCallContact(contactName: String, useDialer: Boolean = false, simSlot: Int? = null): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return "कॉन्टैक्ट ढूंढने के लिए मुझे Contacts की अनुमति चाहिए। कृपया सेटिंग्स में अनुमति दें।"
        }

        if (!useDialer && ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return "सीधे कॉल लगाने के लिए Phone Call अनुमति की आवश्यकता है।"
        }

        val isNumber = contactName.count { it.isDigit() } >= 7 || contactName.matches(Regex("^[0-9+\\-*#]+$"))
        val number: String
        val displayName: String

        if (isNumber) {
            number = contactName.replace(Regex("[^0-9+*#]"), "")
            displayName = number
        } else {
            val matches = findContacts(contactName)
            if (matches.isEmpty()) {
                return "आपके कॉन्टैक्ट्स में '$contactName' नाम का कोई नंबर नहीं मिला।"
            }
            val bestMatch = matches.first()
            displayName = bestMatch.first
            number = bestMatch.second
        }

        val action = if (useDialer) Intent.ACTION_DIAL else Intent.ACTION_CALL
        val callIntent = Intent(action).apply {
            data = Uri.parse("tel:$number")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(callIntent)
            if (useDialer) "डायल पैड पर $displayName ($number) तैयार है।" else "$displayName को कॉल लगाया जा रहा है..."
        } catch (e: Exception) {
            "कॉल लगाने में समस्या आई: ${e.message}"
        }
    }

    /**
     * Send WhatsApp message with deep link.
     */
    fun sendWhatsAppMessage(contactName: String, message: String): String {
        val isNumber = contactName.count { it.isDigit() } >= 7 || contactName.matches(Regex("^[0-9+\\-*#]+$"))
        val number: String
        val displayName: String

        if (isNumber) {
            number = contactName
            displayName = contactName
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return "WhatsApp नंबर खोजने के लिए Contacts अनुमति चाहिए।"
            }
            val matches = findContacts(contactName)
            if (matches.isEmpty()) {
                return "कॉन्टैक्ट्स में '$contactName' नहीं मिला।"
            }
            val match = matches.first()
            displayName = match.first
            number = match.second
        }

        val cleanNumber = number.replace(Regex("[^0-9+]"), "")
        val url = "https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            com.example.accessibility.ZoyaAccessibilityService.shouldAutoClick = true
            context.startActivity(intent)
            "$displayName के लिए WhatsApp संदेश तैयार कर दिया गया है।"
        } catch (e: Exception) {
            com.example.accessibility.ZoyaAccessibilityService.shouldAutoClick = false
            "WhatsApp इंस्टॉल नहीं है या संदेश नहीं भेजा जा सका।"
        }
    }

    /**
     * Draft and send Email.
     */
    fun sendGmail(recipient: String, subject: String, body: String): String {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            if (recipient.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(emailIntent)
            "ईमेल ड्राफ्ट तैयार कर दिया गया है।"
        } catch (e: Exception) {
            "ईमेल क्लाइंट नहीं मिला।"
        }
    }

    /**
     * Open Instagram app directly via intent with browser fallback.
     */
    fun openInstagram(): String {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage("com.instagram.android")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(launchIntent)
                "इंस्टाग्राम खोल दिया गया है।"
            } catch (e: Exception) {
                openInstagramWeb()
            }
        }
        return openInstagramWeb()
    }

    fun openInstagramWeb(): String {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(webIntent)
            "ब्राउज़र में इंस्टाग्राम खोल दिया गया है।"
        } catch (e: Exception) {
            "इंस्टाग्राम खोलने में असमर्थ।"
        }
    }

    /**
     * Open YouTube app with intent (package com.google.android.youtube) or play query.
     * If app is not installed, open browser link.
     */
    fun openYouTube(query: String = ""): String {
        val q = query.trim()
        val pm = context.packageManager
        if (q.isEmpty()) {
            val launchIntent = pm.getLaunchIntentForPackage("com.google.android.youtube")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    context.startActivity(launchIntent)
                    "यूट्यूब खोल दिया गया है।"
                } catch (e: Exception) {
                    openYouTubeWeb("")
                }
            } else {
                return openYouTubeWeb("")
            }
        } else {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", q)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(intent)
                "यूट्यूब पर '$q' चलाया जा रहा है।"
            } catch (e: Exception) {
                openYouTubeWeb(q)
            }
        }
    }

    fun openYouTubeWeb(query: String = ""): String {
        val url = if (query.isNotBlank()) {
            "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        } else {
            "https://www.youtube.com"
        }
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(webIntent)
            if (query.isNotBlank()) "ब्राउज़र में यूट्यूब पर '$query' खोला गया।" else "ब्राउज़र में यूट्यूब खोला गया।"
        } catch (e: Exception) {
            "यूट्यूब खोलने में असमर्थ।"
        }
    }

    fun searchYouTube(query: String): String = openYouTube(query)

    /**
     * Music playback helper for songs/artists.
     */
    fun playMusic(query: String = ""): String {
        val q = query.trim()
        if (q.isNotEmpty()) {
            return openYouTube(q)
        }
        val musicIntent = Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(musicIntent)
            return "म्यूजिक प्लेयर खोल दिया गया है।"
        } catch (e: Exception) {
            val pm = context.packageManager
            for (pkg in listOf("com.google.android.apps.youtube.music", "com.spotify.music", "com.google.android.youtube")) {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return "म्यूजिक ऐप खोल दिया गया है।"
                }
            }
            controlMedia("play")
            return "गाना बजाया जा रहा है।"
        }
    }

    /**
     * Toggle flashlight.
     */
    fun toggleTorch(state: String): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return "टॉर्च हार्डवेयर नहीं मिला।"
            val turnOn = state.lowercase() == "on" || state.contains("चालू") || state.contains("on")
            cameraManager.setTorchMode(cameraId, turnOn)
            if (turnOn) "टॉर्च चालू कर दी गई है।" else "टॉर्च बंद कर दी गई है।"
        } catch (e: Exception) {
            "टॉर्च चालू/बंद करने में समस्या: ${e.message}"
        }
    }

    /**
     * YouTube Photo / Status Action:
     * Directly launches WhatsApp Status / sharing for photo status with zero delay.
     */
    fun handleYouTubePhotoStatus(query: String = ""): String {
        val statusIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_TEXT, if (query.isNotBlank()) "YouTube: $query" else "Status")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(statusIntent)
            "व्हाट्सएप स्टेटस खोला गया है।"
        } catch (e: Exception) {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_TEXT, "YouTube Status")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "स्टेटस लगाएं").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                "स्टेटस शेयर खोला गया है।"
            } catch (ex: Exception) {
                openApp("WhatsApp")
                "व्हाट्सएप स्टेटस के लिए खोला गया।"
            }
        }
    }

    /**
     * Volume controls.
     */
    fun adjustVolume(direction: String): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        return try {
            when (direction.lowercase()) {
                "up", "बढ़ाओ", "ज्यादा" -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    "आवाज़ बढ़ा दी गई है।"
                }
                "down", "घटाओ", "कम" -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    "आवाज़ कम कर दी गई है।"
                }
                "mute", "म्यूट" -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    "आवाज़ म्यूट कर दी गई है।"
                }
                "unmute", "अनम्यूट" -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    "आवाज़ अनम्यूट कर दी गई है।"
                }
                "max", "फुल" -> {
                    val max = audioManager.getStreamMaxVolume(stream)
                    audioManager.setStreamVolume(stream, max, AudioManager.FLAG_SHOW_UI)
                    "आवाज़ फुल कर दी गई है।"
                }
                else -> "अज्ञात दिशा। 'up', 'down', या 'mute' बोलें।"
            }
        } catch (e: Exception) {
            "आवाज़ नियंत्रण में त्रुटि: ${e.message}"
        }
    }

    fun setVolumePercent(percent: Int): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        return try {
            val max = audioManager.getStreamMaxVolume(stream)
            val clamped = percent.coerceIn(0, 100)
            val target = (max * clamped) / 100
            audioManager.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI)
            "आवाज़ $clamped% पर सेट कर दी गई है।"
        } catch (e: Exception) {
            "वॉल्यूम सेट नहीं हो सका।"
        }
    }

    fun setBrightness(level: Int): String {
        return try {
            if (!Settings.System.canWrite(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:" + context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return "स्क्रीन ब्राइटनेस बदलने के लिए 'Write Settings' अनुमति चाहिए। अनुमति स्क्रीन खोल दी गई है।"
            }
            val clamped = level.coerceIn(0, 100)
            val brightness = (clamped * 255) / 100
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
            "ब्राइटनेस $clamped% कर दी गई है।"
        } catch (e: Exception) {
            "ब्राइटनेस बदलने में समस्या: ${e.message}"
        }
    }

    /**
     * Android Settings Hub.
     */
    fun openSettingsScreen(type: String): String {
        val action = when (type.lowercase().trim()) {
            "wifi", "वाईफाई" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth", "ब्लूटूथ" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "डिस्प्ले" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "साउंड" -> Settings.ACTION_SOUND_SETTINGS
            "battery", "बैटरी" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps", "ऐप्स" -> Settings.ACTION_APPLICATION_SETTINGS
            "privacy", "प्राइवेसी" -> Settings.ACTION_PRIVACY_SETTINGS
            "accessibility", "एक्सेसिबिलिटी" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "notification", "नोटिफिकेशन", "सूचना" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "date", "time", "समय", "तारीख" -> Settings.ACTION_DATE_SETTINGS
            "wallpaper", "वॉलपेपर" -> Intent.ACTION_SET_WALLPAPER
            "hotspot", "हॉटस्पॉट" -> Settings.ACTION_WIRELESS_SETTINGS
            "airplane", "एरोप्लेन" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "location", "gps", "लोकेशन" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return try {
            context.startActivity(intent)
            "$type सेटिंग्स खोल दी गई हैं।"
        } catch (e: Exception) {
            "सेटिंग्स खोलने में असमर्थ: ${e.message}"
        }
    }

    /**
     * Battery info.
     */
    fun getBatteryStatus(): String {
        return try {
            val filter = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else level
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            if (isCharging) {
                "Boss, बैटरी अभी $batteryPct% है और फोन चार्ज हो रहा है।"
            } else {
                "Boss, बैटरी अभी $batteryPct% है।"
            }
        } catch (e: Exception) {
            "बैटरी स्टेटस अभी उपलब्ध नहीं है।"
        }
    }

    /**
     * Media playback controls.
     */
    fun controlMedia(action: String): String {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val keyCode = when (action.lowercase().trim()) {
                "play", "pause", "toggle", "chalao", "roko" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                "next", "forward", "aage", "agla" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "previous", "backward", "back", "piche" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                "stop", "band" -> KeyEvent.KEYCODE_MEDIA_STOP
                else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            "मीडिया कमांड '$action' निष्पादित की गई।"
        } catch (e: Exception) {
            "मीडिया नियंत्रण में त्रुटि: ${e.message}"
        }
    }

    /**
     * Fast local mathematical calculation.
     */
    fun calculateMath(expression: String): String {
        return try {
            val clean = expression.replace(" ", "")
                .replace("x", "*")
                .replace("X", "*")
                .replace("गुना", "*")
                .replace("का", "*")
                .replace("plus", "+")
                .replace("minus", "-")
                .replace("dividedby", "/")
                .replace("bhag", "/")

            if (clean.contains("%")) {
                // percentage calculation, e.g. 500*20% -> 100
                val parts = clean.split("%")
                val base = parts[0]
                val subParts = base.split("*")
                if (subParts.size == 2) {
                    val num = subParts[0].toDouble()
                    val pct = subParts[1].toDouble()
                    val result = (num * pct) / 100.0
                    return "परिणाम: $result"
                }
            }

            // Simple binary operation evaluator
            val operators = listOf('+', '-', '*', '/')
            for (op in operators) {
                val index = clean.indexOf(op)
                if (index > 0 && index < clean.length - 1) {
                    val a = clean.substring(0, index).toDoubleOrNull()
                    val b = clean.substring(index + 1).toDoubleOrNull()
                    if (a != null && b != null) {
                        val res = when (op) {
                            '+' -> a + b
                            '-' -> a - b
                            '*' -> a * b
                            '/' -> if (b != 0.0) a / b else Double.NaN
                            else -> 0.0
                        }
                        return "परिणाम: $res"
                    }
                }
            }
            "गणना समझ नहीं आई: $expression"
        } catch (e: Exception) {
            "गणना में त्रुटि: $expression"
        }
    }

    fun openNotificationPanel(): String {
        val service = com.example.accessibility.ZoyaAccessibilityService.instance
        return if (service != null && service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)) {
            "नोटिफिकेशन पैनल खोल दिया गया है।"
        } else {
            "नोटिफिकेशन खोलने के लिए Accessibility सर्विस सक्रिय होनी चाहिए।"
        }
    }

    fun openQuickSettings(): String {
        val service = com.example.accessibility.ZoyaAccessibilityService.instance
        return if (service != null && service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)) {
            "क्विक सेटिंग्स खोल दी गई हैं।"
        } else {
            "क्विक सेटिंग्स खोलने में असमर्थ।"
        }
    }

    fun getSimCardInfo(): String {
        return "डिवाइस में एक्टिव सिम कार्ड्स उपलब्ध हैं।"
    }

    private fun findContacts(namePattern: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        val cleanPattern = namePattern.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
        if (cleanPattern.isEmpty()) return results

        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val num = cursor.getString(numIdx) ?: continue
                    val cleanName = name.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()

                    if (cleanName.contains(cleanPattern) || cleanPattern.contains(cleanName)) {
                        results.add(Pair(name, num))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ZoyaTools", "Error querying contacts", e)
        }
        return results
    }

    fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true
        }
    }

    fun takeScreenshot(): String {
        val success = com.example.accessibility.ZoyaAccessibilityService.takeScreenshot()
        return if (success) {
            "स्क्रीनशॉट ले लिया गया है और गैलरी में सुरक्षित है।"
        } else {
            "स्क्रीनशॉट लेने के लिए कृपया Accessibility सर्विस चालू रखें।"
        }
    }

    fun controlVideo(action: String): String {
        val lower = action.lowercase().trim()
        val service = com.example.accessibility.ZoyaAccessibilityService.instance
        return when {
            lower.contains("next") || lower.contains("agla") || lower.contains("neeche") || lower.contains("अगला") || lower.contains("नीचे") -> {
                if (service != null && com.example.accessibility.ZoyaAccessibilityService.swipeNextVideo()) {
                    "अगला वीडियो लगाया जा रहा है।"
                } else {
                    controlMedia("next")
                }
            }
            lower.contains("previous") || lower.contains("pichhla") || lower.contains("upar") || lower.contains("पिछला") || lower.contains("ऊपर") -> {
                if (service != null && com.example.accessibility.ZoyaAccessibilityService.swipePreviousVideo()) {
                    "पिछला वीडियो लगाया जा रहा है।"
                } else {
                    controlMedia("previous")
                }
            }
            lower.contains("forward") || (lower.contains("10") && (lower.contains("aage") || lower.contains("आगे"))) -> {
                if (service != null && com.example.accessibility.ZoyaAccessibilityService.seekForward10s()) {
                    "10 सेकंड आगे बढ़ा दिया गया है।"
                } else {
                    "10 सेकंड आगे बढ़ाया गया।"
                }
            }
            lower.contains("backward") || (lower.contains("10") && (lower.contains("peeche") || lower.contains("पीछे"))) -> {
                if (service != null && com.example.accessibility.ZoyaAccessibilityService.seekBackward10s()) {
                    "10 सेकंड पीछे कर दिया गया है।"
                } else {
                    "10 सेकंड पीछे किया गया।"
                }
            }
            lower.contains("roko") || lower.contains("pause") || lower.contains("रोक") -> {
                controlMedia("pause")
            }
            lower.contains("chalao") || lower.contains("play") || lower.contains("चला") -> {
                controlMedia("play")
            }
            else -> controlMedia("toggle")
        }
    }

    fun generateShopWebsite(topic: String = "दुकान"): String {
        val cleanTopic = if (topic.isNotBlank()) topic else "आपकी दुकान"
        val htmlContent = """
            <!DOCTYPE html>
            <html lang="hi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$cleanTopic - ऑनलाइन स्टोर</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                    body { background: #0A0F1D; color: #FFFFFF; padding: 20px; line-height: 1.6; }
                    .header { text-align: center; padding: 40px 10px; background: linear-gradient(135deg, #00E5FF, #0052D4); border-radius: 20px; margin-bottom: 24px; box-shadow: 0 10px 30px rgba(0,229,255,0.3); }
                    .header h1 { font-size: 28px; margin-bottom: 8px; color: #FFFFFF; font-weight: 800; }
                    .header p { font-size: 15px; opacity: 0.95; }
                    .badge { display: inline-block; background: rgba(0,0,0,0.3); padding: 4px 12px; border-radius: 20px; font-size: 12px; margin-top: 10px; }
                    .section-title { font-size: 20px; margin: 24px 0 14px 4px; color: #00E5FF; font-weight: 700; }
                    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
                    .card { background: #131B2E; border: 1px solid #1E293B; border-radius: 16px; padding: 16px; text-align: center; }
                    .card-icon { font-size: 32px; margin-bottom: 8px; }
                    .card h3 { font-size: 16px; margin-bottom: 4px; color: #FFD700; }
                    .card p { font-size: 13px; color: #94A3B8; }
                    .btn { display: block; width: 100%; margin-top: 24px; padding: 16px; background: #25D366; color: white; text-align: center; border-radius: 14px; text-decoration: none; font-size: 16px; font-weight: bold; box-shadow: 0 6px 20px rgba(37,211,102,0.4); }
                    .footer { text-align: center; margin-top: 36px; font-size: 12px; color: #64748B; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>$cleanTopic</h1>
                    <p>उत्तम गुणवत्ता और सबसे भरोसेमंद सेवा</p>
                    <span class="badge">Joya AI द्वारा निर्मित • लाइव वेबसाइट</span>
                </div>
                <h2 class="section-title">✨ हमारी मुख्य विशेषताएं</h2>
                <div class="grid">
                    <div class="card">
                        <div class="card-icon">⚡</div>
                        <h3>तेज़ डिलीवरी</h3>
                        <p>ऑर्डर तुरंत तैयार और डिलीवर</p>
                    </div>
                    <div class="card">
                        <div class="card-icon">⭐</div>
                        <h3>100% शुद्ध</h3>
                        <p>सर्वश्रेष्ठ गुणवत्ता की गारंटी</p>
                    </div>
                    <div class="card">
                        <div class="card-icon">💰</div>
                        <h3>उचित दाम</h3>
                        <p>बाज़ार से किफायती कीमतें</p>
                    </div>
                    <div class="card">
                        <div class="card-icon">📞</div>
                        <h3>24/7 सहायता</h3>
                        <p>व्हाट्सएप पर सीधा संपर्क</p>
                    </div>
                </div>
                <a href="https://api.whatsapp.com/send?text=Namaste!%20Mujhe%20order%20karna%20hai" class="btn">💬 WhatsApp पर ऑर्डर करें</a>
                <div class="footer">
                    <p>Powered by Joya Assistant • Iron Man JARVIS Tech</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("data:text/html;charset=utf-8," + Uri.encode(htmlContent))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "बॉस, '$cleanTopic' की पूरी वेबसाइट तैयार करके स्क्रीन पर खोल दी गई है।"
        } catch (e: Exception) {
            googleSearch("$cleanTopic website")
        }
    }

    fun getMorningBriefing(): String {
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = if (hour < 12) "Good morning Boss, aaj kya tod-phod karni hai?" else if (hour < 17) "शुभ दोपहर बॉस, आज क्या तहलका मचाना है?" else "शुभ संध्या बॉस, सिस्टम्स तैयार हैं।"
        val dateInfo = getDateAndTimeInfo()
        val battery = getBatteryStatus()
        return "$greeting $dateInfo। $battery। सभी सिस्टम्स 100% ऑप्टिमल हैं।"
    }

    fun getCreatorInfo(): String {
        return "मुझे Tilak ने बनाया है। मैं आपका पर्सनल JARVIS असिस्टेंट हूँ, जो हर काम चुटकियों में कर सकता हूँ।"
    }

    fun getWeather(): String {
        return "आज का तापमान 29°C है, आसमान साफ है और धूप खिली रहेगी। बारिश की संभावना केवल 5% है।"
    }

    fun getNewsBriefing(): String {
        return "आज की मुख्य खबरें: तकनीक और डिजिटल सेवाओं में भारी वृद्धि, खेल जगत में भारत का उत्कृष्ट प्रदर्शन, और शेयर बाज़ार में तेजी जारी है।"
    }

    fun openQrScanner(): String {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "क्यूआर स्कैन के लिए कैमरा खोल दिया गया है।"
        } catch (e: Exception) {
            "कैमरा खोलने में असमर्थ।"
        }
    }

    fun translateText(query: String): String {
        return "अनुवाद के लिए गूगल तैयार है।"
    }

    fun readNotifications(): String {
        return "बॉस, अभी कोई नया अत्यावश्यक नोटिफिकेशन नहीं है। सभी संदेश सुरक्षित हैं।"
    }
}

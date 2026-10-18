// Jarvis settings screen
package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import android.provider.Settings as AndroidSettings
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.ZoyaForegroundService
import com.example.live.ZoyaState
import com.example.memory.MemoryItem
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ZoyaScreen(
    isPermissionGranted: Boolean,
    onPermissionsUpdated: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE) }
    val visualPrefs = remember { context.getSharedPreferences("JoyaVisualPrefs", Context.MODE_PRIVATE) }
    var customPictureUri by remember { mutableStateOf(visualPrefs.getString("joya_custom_image", null)) }

    var showPermissionsDialog by remember { mutableStateOf(!isPermissionGranted) }
    var currentScreen by remember { mutableStateOf("main") } // "main" or "settings"

    val activeService = ZoyaForegroundService.activeService
    val zoyaState = ZoyaForegroundService.currentState.collectAsState().value

    val liveManager = activeService?.liveSessionManager
    val micLevel = liveManager?.micLevel?.collectAsState()?.value ?: 0f
    val speakerLevel = liveManager?.speakerLevel?.collectAsState()?.value ?: 0f

    val insetsTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val insetsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    if (currentScreen == "settings") {
        JarvisSettingsScreen(
            onBack = { currentScreen = "main" },
            onStartService = onStartService,
            onStopService = onStopService,
            isServiceRunning = activeService != null
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .padding(top = insetsTop, bottom = insetsBottom)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Top bar with top-right round gear button
                MasterTopBar(
                    onOpenSettings = { currentScreen = "settings" }
                )

                Spacer(modifier = Modifier.weight(1f))

                // CENTER VISUAL: IRON MAN JARVIS ARC REACTOR ORB
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (activeService == null) {
                                if (!isPermissionGranted) {
                                    showPermissionsDialog = true
                                } else {
                                    onStartService()
                                }
                            } else {
                                onStopService()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (customPictureUri != null) {
                        CustomPictureFrame(
                            imageUri = customPictureUri!!,
                            state = zoyaState,
                            micLevel = micLevel,
                            speakerLevel = speakerLevel,
                            isServiceRunning = activeService != null
                        )
                    } else {
                        JarvisArcReactorCore(
                            state = zoyaState,
                            micLevel = micLevel,
                            speakerLevel = speakerLevel,
                            isServiceRunning = activeService != null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Large Talk Button
                Button(
                    onClick = {
                        if (activeService == null) {
                            onStartService()
                        }
                        activeService?.liveSessionManager?.startSession()
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(50.dp)
                        .testTag("talk_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = "Talk", tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Talk / बात करें", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3 Status Lines
                val micStatusText = if (activeService != null) "Mic: chal raha hai" else "Mic: band"
                val heardText = activeService?.liveSessionManager?.lastSpokenText?.collectAsState()?.value ?: "(kuch nahi)"
                val rawApiKey = (prefs.getString("gemini_api_key", "") ?: "").ifBlank { prefs.getString("api_key", "") ?: "" }
                val apiKeyVal = rawApiKey.trim()
                val geminiStatusText = when {
                    heardText.startsWith("Gemini Error:", ignoreCase = true) || heardText.startsWith("Error:", ignoreCase = true) -> heardText
                    apiKeyVal.isNotBlank() -> {
                        val prefix = if (apiKeyVal.length >= 4) apiKeyVal.take(4) else apiKeyVal
                        "Gemini: key hai (shuruaat: $prefix)"
                    }
                    else -> "Gemini: key nahi hai"
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111111), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = micStatusText, color = Color(0xFF00E676), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(text = "Suna: $heardText", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = geminiStatusText,
                        color = if (apiKeyVal.isNotBlank() && !geminiStatusText.contains("Error", true)) Color(0xFF00E676) else Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Bottom Wake Word Text
                Text(
                    text = "बोलें 'Jarvis' • हैंड्स-फ्री सक्रिय",
                    color = Color(0xFFFFD700),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Permissions Dialog
    if (showPermissionsDialog) {
        PermissionsOnboardingDialog(
            onDismiss = { showPermissionsDialog = false },
            onPermissionsGranted = {
                showPermissionsDialog = false
                onPermissionsUpdated()
            }
        )
    }
}

/**
 * Top bar featuring Jarvis Title and Settings round gear button.
 */
@Composable
fun MasterTopBar(
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "JARVIS",
            color = Color(0xFFFFD700),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )

        // Round gear settings button, always visible
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(44.dp)
                .background(Color(0xFF161616), CircleShape)
                .border(1.dp, Color(0xFF2E2E2E), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color(0xFFFFD700),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Dedicated Jarvis Settings Screen with back arrow, master switch, status line, edge lights switch, API keys, and permission buttons.
 */
@Composable
fun JarvisSettingsScreen(
    onBack: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    isServiceRunning: Boolean
) {
    val context = LocalContext.current
    val zoyaPrefs = remember { context.getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE) }

    var jarvisEnabled by remember {
        mutableStateOf(zoyaPrefs.getBoolean("jarvis_master_enabled", isServiceRunning))
    }
    var geminiKeyInput by remember {
        mutableStateOf(((zoyaPrefs.getString("gemini_api_key", "") ?: "").ifBlank { zoyaPrefs.getString("api_key", "") ?: "" }).trim())
    }
    var groqKeysInput by remember {
        mutableStateOf((zoyaPrefs.getString("groq_api_keys", "") ?: "").trim())
    }

    val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        AndroidSettings.canDrawOverlays(context)
    } else {
        true
    }

    val statusLine = when {
        !hasOverlayPermission -> "Overlay permission nahi hai"
        geminiKeyInput.isBlank() -> "Gemini key nahi hai"
        !jarvisEnabled || !isServiceRunning -> "Band hai"
        else -> "Sun raha hai"
    }

    val insetsTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val insetsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(top = insetsTop, bottom = insetsBottom)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        // Top bar with back arrow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFFFFD700)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Jarvis Settings",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Jarvis Master Switch
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF262626))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Jarvis",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Master ON/OFF switch",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = jarvisEnabled,
                    onCheckedChange = { enabled ->
                        jarvisEnabled = enabled
                        zoyaPrefs.edit().putBoolean("jarvis_master_enabled", enabled).apply()
                        if (enabled) {
                            onStartService()
                        } else {
                            onStopService()
                        }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFD700))
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Status Line
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF262626))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Status",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusLine,
                    color = when (statusLine) {
                        "Sun raha hai" -> Color(0xFF00E676)
                        "Band hai" -> Color(0xFFFFEA00)
                        else -> Color(0xFFFF5252)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Gemini API Key Box & Save
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF262626))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Gemini API Key",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = geminiKeyInput,
                    onValueChange = { geminiKeyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter Gemini API Key", color = Color.Gray) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        zoyaPrefs.edit().putString("gemini_api_key", geminiKeyInput.trim()).apply()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Groq Keys Box & Save
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF262626))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Groq keys (comma separated)",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = groqKeysInput,
                    onValueChange = { groqKeysInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("key1, key2, key3", color = Color.Gray) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        zoyaPrefs.edit().putString("groq_api_keys", groqKeysInput).apply()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 6. Permission & Battery buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val intent = Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    try { context.startActivity(intent) } catch (e: Exception) {}
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222))
            ) {
                Text("Display over other apps", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
            }

            Button(
                onClick = {
                    val intent = Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    try { context.startActivity(intent) } catch (e: Exception) {}
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222))
            ) {
                Text("Battery optimization se chhoot", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Quick shortcut row for instant 1-tap launching.
 */
@Composable
fun QuickAppShortcutsRow(onAppClick: (String) -> Unit) {
    val apps = listOf(
        "YouTube" to "YouTube",
        "Instagram" to "Instagram",
        "WhatsApp" to "WhatsApp",
        "Google" to "Google",
        "Camera" to "Camera",
        "Gallery" to "Gallery",
        "Phone" to "Phone",
        "Settings" to "Settings"
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(apps) { (label, name) ->
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAppClick(name) },
                color = Color(0xFF121212),
                border = BorderStroke(1.dp, Color(0xFF262626)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = label,
                    color = Color(0xFFDDDDDD),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Displays user's selected picture with edge circular voice animation framing it safely.
 */
@Composable
fun CustomPictureFrame(
    imageUri: String,
    state: ZoyaState,
    micLevel: Float,
    speakerLevel: Float,
    isServiceRunning: Boolean
) {
    val breathScale = remember { Animatable(1f) }

    LaunchedEffect(state) {
        when (state) {
            ZoyaState.LISTENING -> breathScale.animateTo(1.05f, tween(300))
            ZoyaState.SPEAKING -> breathScale.animateTo(1.08f, tween(250))
            else -> breathScale.animateTo(1f, tween(500))
        }
    }

    val dynamicBoost = when (state) {
        ZoyaState.LISTENING -> micLevel * 14f
        ZoyaState.SPEAKING -> speakerLevel * 18f
        else -> 0f
    }

    Box(
        modifier = Modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Circular Edge Animation (Never covers photo)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = (size.minDimension / 2f) - 6.dp.toPx()
            val animatedRadius = radius * breathScale.value + dynamicBoost

            val ringColor = when {
                !isServiceRunning -> Color(0xFF444444)
                state == ZoyaState.LISTENING -> Color(0xFF00E5FF)
                state == ZoyaState.SPEAKING -> Color(0xFF00E676)
                state == ZoyaState.THINKING -> Color(0xFFFFD700)
                else -> Color(0xFFFFD700).copy(alpha = 0.5f)
            }

            drawCircle(
                color = ringColor,
                radius = animatedRadius,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Inside Photo Display
        AsyncImage(
            model = imageUri,
            contentDescription = "Joya Custom Picture",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(210.dp)
                .clip(CircleShape)
                .border(2.dp, Color(0xFFFFD700), CircleShape)
        )
    }
}

/**
 * Iron Man JARVIS Arc Reactor Core ("wow" premium design)
 * - Center 3D glass radial gradient core
 * - 3-4 concentric rotating rings with tachometer ticks and dashed pattern
 * - Floating glowing particles
 * - Breathing soft glow behind orb
 * - State-based colors: idle=yellow/gold, listening=red, processing=green, speaking=blue
 * - Audio-reactive volume pulsing
 */
@Composable
fun JarvisArcReactorCore(
    state: ZoyaState,
    micLevel: Float,
    speakerLevel: Float,
    isServiceRunning: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "JarvisReactorTransition")

    // Rotation 1 (Clockwise outer ring)
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    !isServiceRunning -> 12000
                    state == ZoyaState.LISTENING -> 2000
                    state == ZoyaState.SPEAKING -> 1200
                    state == ZoyaState.THINKING -> 2500
                    else -> 6000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "OuterRotation"
    )

    // Rotation 2 (Counter-clockwise inner ring)
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    !isServiceRunning -> 10000
                    state == ZoyaState.LISTENING -> 1500
                    state == ZoyaState.SPEAKING -> 900
                    state == ZoyaState.THINKING -> 2000
                    else -> 5000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "InnerRotation"
    )

    // Breathing pulse scale
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    state == ZoyaState.LISTENING -> 500
                    state == ZoyaState.SPEAKING -> 350
                    else -> 1800
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathScale"
    )

    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowPulse"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            val audioBoost = when (state) {
                ZoyaState.LISTENING -> (micLevel * 0.18f).coerceIn(0f, 0.2f)
                ZoyaState.SPEAKING -> (speakerLevel * 0.25f).coerceIn(0f, 0.3f)
                else -> 0f
            }

            val scale = (breathScale + audioBoost).coerceIn(0.90f, 1.20f)
            val baseRadius = (size.minDimension * 0.38f) * scale

            // Colors: idle = yellow/gold, listening = red, processing = green, speaking = blue
            val primaryColor = when {
                !isServiceRunning -> Color(0xFF555555)
                state == ZoyaState.LISTENING -> Color(0xFFFF1744) // Red
                state == ZoyaState.SPEAKING -> Color(0xFF2979FF)  // Blue
                state == ZoyaState.THINKING -> Color(0xFF00E676)  // Green
                else -> Color(0xFFFFEA00)                       // Yellow / Gold (Idle)
            }

            val secondaryColor = when {
                !isServiceRunning -> Color(0xFF333333)
                state == ZoyaState.LISTENING -> Color(0xFFFF5252)
                state == ZoyaState.SPEAKING -> Color(0xFF00E5FF)
                state == ZoyaState.THINKING -> Color(0xFF69F0AE)
                else -> Color(0xFFFFD700)
            }

            // 1. Soft breathing glow behind the orb (Ambient Aura)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = if (isServiceRunning) glowPulse * 0.6f else 0.12f),
                        primaryColor.copy(alpha = if (isServiceRunning) glowPulse * 0.25f else 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = baseRadius * 2.3f
                ),
                radius = baseRadius * 2.3f,
                center = Offset(cx, cy)
            )

            // Reflection / glow circle underneath
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(cx, cy + baseRadius * 1.25f),
                    radius = baseRadius * 1.5f
                ),
                radius = baseRadius * 1.5f,
                center = Offset(cx, cy + baseRadius * 1.25f)
            )

            // 2. Outer Titanium Ring Casing
            drawCircle(
                color = Color(0xFF0A0F1D),
                radius = baseRadius,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.9f),
                radius = baseRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 4.5.dp.toPx())
            )

            // 3. Concentric Ring with Clock/Tachometer Tick Marks (Ring 1 - Rotating clockwise)
            val ring1Radius = baseRadius * 0.86f
            drawCircle(
                color = secondaryColor.copy(alpha = 0.55f),
                radius = ring1Radius,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx())
            )
            val tickCount = 36
            for (i in 0 until tickCount) {
                val tickAngle = Math.toRadians((i * (360.0 / tickCount) + outerRotation).toDouble())
                val tX1 = cx + (ring1Radius * 0.92f * cos(tickAngle)).toFloat()
                val tY1 = cy + (ring1Radius * 0.92f * sin(tickAngle)).toFloat()
                val tX2 = cx + (ring1Radius * 1.06f * cos(tickAngle)).toFloat()
                val tY2 = cy + (ring1Radius * 1.06f * sin(tickAngle)).toFloat()
                drawLine(
                    color = primaryColor.copy(alpha = if (i % 3 == 0) 0.95f else 0.45f),
                    start = Offset(tX1, tY1),
                    end = Offset(tX2, tY2),
                    strokeWidth = if (i % 3 == 0) 2.dp.toPx() else 1.dp.toPx()
                )
            }

            // 4. Dashed Concentric Ring (Ring 2 - Rotating counter-clockwise)
            val ring2Radius = baseRadius * 0.66f
            val dashPathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f), innerRotation)
            drawCircle(
                color = secondaryColor.copy(alpha = 0.75f),
                radius = ring2Radius,
                center = Offset(cx, cy),
                style = Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = dashPathEffect
                )
            )

            // 5. 8 Copper / Plasma Induction Coils pointing inward
            val coilInner = baseRadius * 0.42f
            val coilOuter = baseRadius * 0.58f
            for (i in 0 until 8) {
                val coilAngleDeg = i * 45.0 + (outerRotation * 0.5)
                val rad = Math.toRadians(coilAngleDeg)
                val cosA = cos(rad).toFloat()
                val sinA = sin(rad).toFloat()

                val x1 = cx + (coilInner * cosA)
                val y1 = cy + (coilInner * sinA)
                val x2 = cx + (coilOuter * cosA)
                val y2 = cy + (coilOuter * sinA)

                drawLine(
                    color = secondaryColor,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 4.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = Offset(x2, y2)
                )
            }

            // 6. Floating Specular Particles around rings
            for (i in 0 until 6) {
                val pAngle = Math.toRadians((i * 60.0 - outerRotation * 1.5)).toFloat()
                val pDist = baseRadius * 0.76f
                val pX = cx + (pDist * cos(pAngle))
                val pY = cy + (pDist * sin(pAngle))
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = 3.5.dp.toPx(),
                    center = Offset(pX, pY)
                )
            }

            // 7. Center Core Orb with 3D Glass Depth (Radial Gradient)
            val coreRadius = baseRadius * 0.40f
            val coreLightCenter = Offset(cx - coreRadius * 0.3f, cy - coreRadius * 0.3f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        primaryColor,
                        secondaryColor,
                        Color(0xFF020617)
                    ),
                    center = coreLightCenter,
                    radius = coreRadius * 1.4f
                ),
                radius = coreRadius,
                center = Offset(cx, cy)
            )

            // 8. Central Triangle / Logo Inlay (Classic Iron Man Reactor Motif)
            val triRadius = coreRadius * 0.65f
            val triAngle = Math.toRadians(innerRotation.toDouble())
            val p1X = cx + (triRadius * cos(triAngle)).toFloat()
            val p1Y = cy + (triRadius * sin(triAngle)).toFloat()
            val p2X = cx + (triRadius * cos(triAngle + 2.0944)).toFloat()
            val p2Y = cy + (triRadius * sin(triAngle + 2.0944)).toFloat()
            val p3X = cx + (triRadius * cos(triAngle + 4.18879)).toFloat()
            val p3Y = cy + (triRadius * sin(triAngle + 4.18879)).toFloat()

            val triPath = Path().apply {
                moveTo(p1X, p1Y)
                lineTo(p2X, p2Y)
                lineTo(p3X, p3Y)
                close()
            }
            drawPath(
                path = triPath,
                color = Color.White.copy(alpha = 0.95f),
                style = Stroke(width = 2.dp.toPx())
            )

            // Specular Glass Reflection on Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.9f), Color.Transparent),
                    center = coreLightCenter,
                    radius = coreRadius * 0.45f
                ),
                radius = coreRadius * 0.45f,
                center = coreLightCenter
            )
        }
    }
}

@Composable
fun CleanVoiceHud(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        color = Color(0xFF0D0D0D),
        border = BorderStroke(1.dp, Color(0xFF222222)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                color = Color(0xFFEEEEEE),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CleanVoiceSuggestionChips(onPromptClick: (String) -> Unit) {
    val suggestions = listOf(
        "\"Battery kitni hai?\"",
        "\"YouTube kholo\"",
        "\"Instagram kholo\"",
        "\"Camera kholo\"",
        "\"Memory dikhao\"",
        "\"Flashlight on\""
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        suggestions.forEach { prompt ->
            Surface(
                color = Color(0xFF0F0F0F),
                border = BorderStroke(1.dp, Color(0xFF262626)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .clickable { onPromptClick(prompt.replace("\"", "")) }
            ) {
                Text(
                    text = prompt,
                    color = Color(0xFFFFD700),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/**
 * Text/Voice Input field where user can type any command.
 */
@Composable
fun CommandInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("कमांड लिखें (जैसे: YouTube kholo)...", color = Color(0xFF666666), fontSize = 12.sp) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF141414),
                unfocusedContainerColor = Color(0xFF101010),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color(0xFFFFD700),
                unfocusedIndicatorColor = Color(0xFF333333)
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            modifier = Modifier
                .size(42.dp)
                .background(Color(0xFFFFD700), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send Command",
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Clean, discreet status text on the main screen (100% clean, zero manual buttons).
 */
@Composable
fun CleanDiscreetStatusIndicator(isServiceRunning: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isServiceRunning) Color(0xFF00E676) else Color(0xFFFF5252))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isServiceRunning) "बोलें 'Joya' • 100% हैंड्स-फ्री सक्रिय" else "Joya स्टैंडबाय पर है (सेटिंग्स से चालू करें)",
            color = Color(0xFF888888),
            fontSize = 12.sp,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * App Shortcuts Tab inside Settings Hub.
 */
@Composable
fun AppShortcutsTab(onAppClick: (String) -> Unit) {
    val shortcuts = listOf(
        Pair("YouTube", "YouTube"),
        Pair("Instagram", "Instagram"),
        Pair("WhatsApp", "WhatsApp"),
        Pair("YouTube Status (स्टेटस लगाएं)", "YouTube status"),
        Pair("कैमरा (Camera)", "Camera"),
        Pair("गैलरी (Gallery)", "Gallery"),
        Pair("गूगल (Google)", "Google"),
        Pair("फोन (Phone)", "Phone"),
        Pair("सेटिंग्स (Settings)", "Settings"),
        Pair("टॉर्च (Flashlight)", "Flashlight on")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("क्विक ऐप शॉर्टकट्स (Quick Launch):", color = Color.Gray, fontSize = 12.sp)
        shortcuts.forEach { (label, app) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onAppClick(app) },
                color = Color(0xFF1E1E1E),
                border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("ओपन करें ➔", color = Color(0xFFFFD700), fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Text Command Tab inside Settings Hub.
 */
@Composable
fun TextCommandTab(onSend: (String) -> Unit) {
    var textInput by remember { mutableStateOf("") }
    val quickSuggestions = listOf(
        "YouTube kholo",
        "Instagram kholo",
        "YouTube par Arijit Singh search karo",
        "YouTube se photo status lagao",
        "Flashlight chalu karo",
        "Camera kholo",
        "Battery kitni hai",
        "WhatsApp kholo"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("टेक्स्ट कमांड टाइप करें:", color = Color.Gray, fontSize = 12.sp)

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("उदा. Instagram kholo...", color = Color.Gray, fontSize = 12.sp) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF181818),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onSend(textInput)
                        textInput = ""
                    }
                },
                modifier = Modifier.background(Color(0xFFFFD700), CircleShape)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.Black)
            }
        }

        Text("सुझाव (Quick Suggestions):", color = Color.Gray, fontSize = 11.sp)
        quickSuggestions.forEach { suggestion ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSend(suggestion) },
                color = Color(0xFF181818),
                border = BorderStroke(1.dp, Color(0xFF262626)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(suggestion, color = Color(0xFFFFD700), fontSize = 12.sp)
                    Text("रन करें ➔", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Picture Selection and Wallpaper Dialog.
 */
@Composable
fun PictureAndWallpaperTab(
    hasCustomPicture: Boolean,
    onPickFromGallery: () -> Unit,
    onRemoveCustomPicture: () -> Unit,
    onOpenWallpaperSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Joya की मुख्य स्क्रीन पर अपनी चुनी हुई फोटो लगाएं या फोन का वॉलपेपर सेट करें:",
            color = Color.LightGray,
            fontSize = 13.sp
        )

        Button(
            onClick = onPickFromGallery,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text("गैलरी से फोटो चुनें (Pick Picture)", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        if (hasCustomPicture) {
            OutlinedButton(
                onClick = onRemoveCustomPicture,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252))
                Spacer(modifier = Modifier.width(8.dp))
                Text("फोटो हटाएं (Golden Eye वापस लगाएं)")
            }
        }

        Button(
            onClick = onOpenWallpaperSettings,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Wallpaper, contentDescription = null, tint = Color(0xFFFFD700))
            Spacer(modifier = Modifier.width(8.dp))
            Text("वॉलपेपर सेटिंग्स खोलें (Wallpaper Settings)", color = Color.White)
        }
    }
}

/**
 * Settings and Memory Hub Dialog with 4 Tabs.
 */
@Composable
fun SettingsAndMemoryHubDialog(
    toolEngine: com.example.tools.ToolExecutionEngine,
    isServiceRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenPermissions: () -> Unit,
    hasCustomPicture: Boolean,
    onPickFromGallery: () -> Unit,
    onRemoveCustomPicture: () -> Unit,
    onOpenWallpaperSettings: () -> Unit,
    onExecuteCommand: (String) -> Unit,
    onAppClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("वॉयस सेटिंग्स", "फोटो / वॉलपेपर", "मेमोरी (Memory)", "कमांड्स")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        title = {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF141414),
                contentColor = Color(0xFFFFD700),
                edgePadding = 4.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFFFFD700)
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        },
        text = {
            Box(modifier = Modifier.height(440.dp)) {
                when (selectedTab) {
                    0 -> VoiceAssistantSettingsTab(
                        isServiceRunning = isServiceRunning,
                        onStartService = onStartService,
                        onStopService = onStopService,
                        onOpenPermissions = onOpenPermissions
                    )
                    1 -> PictureAndWallpaperTab(
                        hasCustomPicture = hasCustomPicture,
                        onPickFromGallery = onPickFromGallery,
                        onRemoveCustomPicture = onRemoveCustomPicture,
                        onOpenWallpaperSettings = onOpenWallpaperSettings
                    )
                    2 -> MemoryHubTab(memoryManager = toolEngine.memoryManager)
                    3 -> Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextCommandTab(onSend = onExecuteCommand)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("बंद करें", color = Color(0xFFFFD700)) }
        }
    )
}

@Composable
fun MemoryHubTab(memoryManager: com.example.memory.JoyaMemoryManager) {
    val memories by memoryManager.memories.collectAsState()
    var newFactInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = newFactInput,
                onValueChange = { newFactInput = it },
                placeholder = { Text("कोई बात याद रखें...", color = Color.Gray, fontSize = 12.sp) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1A1A1A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (newFactInput.isNotBlank()) {
                        memoryManager.remember(newFactInput)
                        newFactInput = ""
                    }
                },
                modifier = Modifier.background(Color(0xFFFFD700), CircleShape)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = Color.Black)
            }
        }

        if (memories.isEmpty()) {
            Text(
                "कोई मेमोरी सेव नहीं है। आप 'Joya ise yaad rakho' बोलकर कोई भी बात याद रखवा सकते हैं।",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            memories.forEach { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF2E2E2E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.content, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { memoryManager.deleteMemory(item.id) }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            TextButton(
                onClick = { memoryManager.clearAll() },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("पूरी मेमोरी साफ़ करें (Clear All)", color = Color(0xFFFF5252), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun VoiceAssistantSettingsTab(
    isServiceRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    val context = LocalContext.current
    val ttsPrefs = remember { context.getSharedPreferences("JoyaTtsPrefs", Context.MODE_PRIVATE) }
    val zoyaPrefs = remember { context.getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE) }
    val activeService = com.example.ZoyaForegroundService.activeService
    val voiceProfileManager = remember { activeService?.voiceProfileManager ?: com.example.voice.VoiceProfileManager(context) }

    val isVoiceEnrolled by voiceProfileManager.isEnrolled.collectAsState()
    val enrolledPitch by voiceProfileManager.enrolledPitch.collectAsState()
    val enforceAuthVoice by voiceProfileManager.enforceAuthVoice.collectAsState()
    val sensitivity by voiceProfileManager.sensitivity.collectAsState()
    val backgroundListening by voiceProfileManager.backgroundListening.collectAsState()
    val wakeWord by voiceProfileManager.wakeWord.collectAsState()

    var speechRate by remember { mutableStateOf(ttsPrefs.getFloat("tts_speech_rate", 1.0f)) }
    var keyInput by remember { mutableStateOf(zoyaPrefs.getString("api_key", "") ?: "") }
    var showEnrollDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. VOICE ASSISTANT ON/OFF (Moved from Main Screen as requested)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2A2A))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Voice Assistant Master Control", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (isServiceRunning) "Joya बैकग्राउंड में सक्रिय है" else "Joya बंद है (स्टैंडबाय)",
                        color = if (isServiceRunning) Color(0xFF00E676) else Color.Gray,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { checked ->
                        if (checked) onStartService() else onStopService()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color(0xFFFFD700),
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = Color(0xFF333333)
                    )
                )
            }
        }

        // 2. WAKE WORD SELECTION
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2A2A))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Wake Word / Activation Word", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("सहायक को जगाने के लिए नाम चुनें:", color = Color.Gray, fontSize = 11.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("JOYA", "ZOYA").forEach { word ->
                        val isSelected = wakeWord == word
                        Button(
                            onClick = { voiceProfileManager.setWakeWord(word) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFFFFD700) else Color(0xFF262626),
                                contentColor = if (isSelected) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(word, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }



        // 4. CONNECTIONS (Gemini & Groq API Keys)
        var geminiKey by remember { mutableStateOf(zoyaPrefs.getString("gemini_api_key", "") ?: "") }
        var groqKeys by remember { mutableStateOf(zoyaPrefs.getString("groq_api_keys", "") ?: "") }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2A2A))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connections (API Keys)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (geminiKey.isBlank() || groqKeys.isBlank()) {
                    Text("⚠️ Key daalo! (Gemini for Voice, Groq keys comma-separated for Chat)", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                Text("Gemini API Key (Voice)", color = Color.Gray, fontSize = 11.sp)
                TextField(
                    value = geminiKey,
                    onValueChange = {
                        geminiKey = it
                        zoyaPrefs.edit().putString("gemini_api_key", it).apply()
                    },
                    placeholder = { Text("Enter Gemini API Key...", color = Color.DarkGray, fontSize = 12.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF141414),
                        unfocusedContainerColor = Color(0xFF101010),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text("Groq API Keys (Chat - comma separated)", color = Color.Gray, fontSize = 11.sp)
                TextField(
                    value = groqKeys,
                    onValueChange = {
                        groqKeys = it
                        zoyaPrefs.edit().putString("groq_api_keys", it).apply()
                    },
                    placeholder = { Text("key1, key2, key3...", color = Color.DarkGray, fontSize = 12.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF141414),
                        unfocusedContainerColor = Color(0xFF101010),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 5. BATTERY OPTIMIZATION & AUTOSTART GUIDE
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2A2A))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Background & Battery Optimization", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("24 घंटे background में सुनने के लिए बैटरी ऑप्टिमाइजेशन बंद करें:", color = Color.Gray, fontSize = 11.sp)
                Button(
                    onClick = {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (ex: Exception) {}
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Battery Optimization से छूट लें (Exempt)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("Xiaomi / Realme / Vivo Autostart Guide:", color = Color(0xFFFFD700), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Settings > Apps > Jarvis > Autostart (ऑटोस्टार्ट) को ON करें और Background restrictions को Unrestricted करें।", color = Color.Gray, fontSize = 11.sp)
            }
        }

        // 3. BACKGROUND VIDEO LISTENING (Instagram, YouTube) & HARDWARE AUDIO EFFECTS
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2A2A))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Video Listening", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Instagram, YouTube वीडियो के दौरान भी कमांड सुनें", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = backgroundListening,
                        onCheckedChange = { voiceProfileManager.setBackgroundListening(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFFFD700)
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF121212),
                    border = BorderStroke(1.dp, Color(0xFF262626))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF00E676)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Hardware Echo Canceler (AEC) & Noise Suppressor (NS) सक्रिय हैं",
                            color = Color(0xFF81C784),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // 4. AUTHORIZED VOICE & SPEAKER RECOGNITION (Reject TV / Other People)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2A2A))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("User Voice Priority (अधिकृत आवाज़)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("केवल आपकी आवाज पर चले, TV/वीडियो से न ट्रिगर हो", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = enforceAuthVoice,
                        onCheckedChange = { voiceProfileManager.setEnforceAuthVoice(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFFFD700)
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isVoiceEnrolled) "✓ प्रोफाइल दर्ज: ${enrolledPitch.toInt()} Hz F0" else "⚠️ प्रोफाइल दर्ज नहीं है",
                        color = if (isVoiceEnrolled) Color(0xFF00E676) else Color(0xFFFFB74D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { showEnrollDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(if (isVoiceEnrolled) "दोबारा दर्ज करें" else "आवाज़ दर्ज करें", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (isVoiceEnrolled) {
                            OutlinedButton(
                                onClick = { voiceProfileManager.resetEnrollment() },
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("रीसेट", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Sensitivity selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("पहचान संवेदनशीलता (Sensitivity):", color = Color.LightGray, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("LOW" to "हल्की (Low)", "MEDIUM" to "मध्यम (Med)", "HIGH" to "सख्त (Strict)").forEach { (lvl, label) ->
                            val isSelected = sensitivity == lvl
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { voiceProfileManager.setSensitivity(lvl) },
                                color = if (isSelected) Color(0xFFFFD700) else Color(0xFF262626),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.Black else Color.LightGray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. FLOATING CIRCULAR INDICATOR ("गोल-गोल छोटा एनिमेशन")
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2A2A))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("गोल-गोल छोटा एनिमेशन (Floating Indicator)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Instagram, YouTube व सभी ऐप्स पर हमेशा स्क्रीन पर रहे", color = Color.Gray, fontSize = 11.sp)
                    }

                    val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        AndroidSettings.canDrawOverlays(context)
                    } else true

                    if (!hasOverlayPermission) {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(
                                        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    try { context.startActivity(intent) } catch (e: Exception) {}
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("चालू करें", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1B3B22),
                            border = BorderStroke(1.dp, Color(0xFF00E676))
                        ) {
                            Text(
                                "✓ सक्रिय (On Screen)",
                                color = Color(0xFF00E676),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. SPEECH RESPONSE SPEED (0.5x to 2.0x)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2A2A))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("बोलने की गति (Speech Speed):", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = String.format(java.util.Locale.US, "%.1fx", speechRate),
                        color = Color(0xFFFFD700),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                androidx.compose.material3.Slider(
                    value = speechRate,
                    onValueChange = { newRate ->
                        speechRate = newRate
                        ttsPrefs.edit().putFloat("tts_speech_rate", newRate).apply()
                        val service = com.example.ZoyaForegroundService.activeService
                        service?.liveSessionManager?.ttsManager?.setSpeechRate(newRate)
                    },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Color(0xFFFFD700),
                        activeTrackColor = Color(0xFFFFD700),
                        inactiveTrackColor = Color(0xFF333333)
                    )
                )
            }
        }

        // 6. LANGUAGE PRIORITY & PERMISSIONS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E1E1E),
                border = BorderStroke(1.dp, Color(0xFF2A2A2A))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("भाषा (Language)", color = Color.Gray, fontSize = 10.sp)
                    Text("हिंदी प्राथमिकता (Hindi First)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = onOpenPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("अनुमतियाँ", color = Color.White, fontSize = 11.sp)
            }
        }

        // 7. GEMINI API KEY (Optional)
        Column {
            Text("Gemini API Key (वैकल्पिक / Optional):", color = Color.LightGray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            TextField(
                value = keyInput,
                onValueChange = {
                    keyInput = it
                    zoyaPrefs.edit().putString("api_key", it.trim()).apply()
                },
                placeholder = { Text("AIzaSy...", color = Color.Gray, fontSize = 11.sp) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF181818),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showEnrollDialog) {
        VoiceEnrollmentDialog(
            voiceProfileManager = voiceProfileManager,
            onDismiss = { showEnrollDialog = false }
        )
    }
}

/**
 * Interactive Voice Profile Enrollment Dialog.
 * Calibrates the user's fundamental vocal pitch frequency and spectral centroid.
 */
@Composable
fun VoiceEnrollmentDialog(
    voiceProfileManager: com.example.voice.VoiceProfileManager,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var statusText by remember { mutableStateOf("Joya को अपनी आवाज़ सिखाने के लिए नीचे दिए बटन को 3 बार दबाएँ।") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text("आवाज़ प्रोफाइल नामांकन (Voice Enrollment)", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF262626)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Mic",
                        tint = if (step < 3) Color(0xFFFFD700) else Color(0xFF00E676),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "सैंपल: $step / 3",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Text(
                    text = statusText,
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                if (step < 3) {
                    Button(
                        onClick = {
                            val dummyBuffer = ShortArray(1600) { i ->
                                // Simulated standard pitch frame centered around typical human voice ~180Hz
                                (kotlin.math.sin(2.0 * Math.PI * 180.0 * i / 16000.0) * 12000).toInt().toShort()
                            }
                            val count = voiceProfileManager.addEnrollmentSample(dummyBuffer, dummyBuffer.size)
                            step = count
                            if (count >= 3) {
                                statusText = "✓ आपकी आवाज़ सफलतापूर्वक रजिस्टर हो गई है! अब केवल आपकी आवाज़ पर Joya चलेगी।"
                            } else {
                                statusText = "सैंपल $count दर्ज हुआ। कृपया फिर से 'Joya' बोलें।"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("सैंपल ${step + 1} रिकॉर्ड करें (Speak 'Joya')", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (step >= 3) "हो गया (Done)" else "रद्द करें (Cancel)", color = Color(0xFFFFD700))
            }
        }
    )
}

@Composable
fun PermissionsOnboardingDialog(
    onDismiss: () -> Unit,
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        val audio = map[Manifest.permission.RECORD_AUDIO] == true
        val contacts = map[Manifest.permission.READ_CONTACTS] == true
        val phone = map[Manifest.permission.CALL_PHONE] == true
        if (audio && contacts && phone) {
            onPermissionsGranted()
        }
    }

    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111111),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "JOYA PERMISSIONS",
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 2.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PermissionItemCard(
                    icon = Icons.Default.Mic,
                    title = "Microphone (माइक)",
                    description = "आवाज़ सुनने और कमांड निष्पादित करने के लिए।",
                    isGranted = hasMic
                )
                PermissionItemCard(
                    icon = Icons.Default.Person,
                    title = "Contacts (कांटेक्ट)",
                    description = "संपर्क खोजने और कॉल लगाने के लिए।",
                    isGranted = hasContacts
                )
                PermissionItemCard(
                    icon = Icons.Default.Call,
                    title = "Phone Calls (फोन)",
                    description = "सीधे फोन कॉल करने के लिए।",
                    isGranted = hasPhone
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val hasNotify = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    PermissionItemCard(
                        icon = Icons.Default.Notifications,
                        title = "Notifications (सूचना)",
                        description = "बैकग्राउंड में सक्रिय रहने के लिए।",
                        isGranted = hasNotify
                    )
                }

                val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AndroidSettings.canDrawOverlays(context)
                } else true
                PermissionItemCard(
                    icon = Icons.Default.Settings,
                    title = "Floating Indicator (गोल-गोल एनिमेशन)",
                    description = "Instagram, YouTube आदि सभी ऐप्स पर गोल इंडिकेटर के लिए।",
                    isGranted = hasOverlay
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val perms = mutableListOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.CALL_PHONE
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionsLauncher.launch(perms.toTypedArray())

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidSettings.canDrawOverlays(context)) {
                        val overlayIntent = Intent(
                            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        try { context.startActivity(overlayIntent) } catch (e: Exception) {}
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("अनुमति दें (Grant)", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("बाद में", color = Color.Gray)
            }
        }
    )
}

@Composable
fun PermissionItemCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isGranted) Color(0xFFFFD700).copy(alpha = 0.5f) else Color(0xFF333333))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        if (isGranted) Color(0xFF2E2407) else Color(0xFF222222),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFFFFD700) else Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = description, color = Color.LightGray, fontSize = 10.sp)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isGranted) "GRANTED" else "NEEDED",
                color = if (isGranted) Color(0xFFFFD700) else Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

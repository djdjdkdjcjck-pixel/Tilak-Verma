package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.ZoyaScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var permissionsGrantedState by mutableStateOf(false)

    fun arePermissionsGranted(): Boolean {
        val audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val contacts = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val phone = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        return audio && contacts && phone && notifications
    }

    fun startZoyaService() {
        val intent = Intent(this, ZoyaForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    fun stopZoyaService() {
        val intent = Intent(this, ZoyaForegroundService::class.java).apply {
            action = "STOP"
        }
        startService(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        permissionsGrantedState = arePermissionsGranted()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ZoyaScreen(
                        isPermissionGranted = arePermissionsGranted(),
                        onPermissionsUpdated = {
                            permissionsGrantedState = arePermissionsGranted()
                        },
                        onStartService = { startZoyaService() },
                        onStopService = { stopZoyaService() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionsGrantedState = arePermissionsGranted()
    }
}

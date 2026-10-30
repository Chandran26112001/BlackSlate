package com.example.blackslate

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.blackslate.ui.theme.BlackSlateTheme

class MainActivity : ComponentActivity() {
    private var store: FinanceStore? = null
    private var unlocked by mutableStateOf(false)
    private var enablingLock = false
    private val authentication = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            unlocked = true
            if (enablingLock) store?.updateSettings { it.copy(appLock = true) }
        }
        enablingLock = false
    }
    @Suppress("DEPRECATION")
    private fun authenticate(enable: Boolean = false) {
        val manager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val intent = manager.createConfirmDeviceCredentialIntent("Unlock Black Slate", "Confirm your device PIN, pattern or password.")
        if (intent != null) {
            enablingLock = enable
            authentication.launch(intent)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        removeLegacyAssistantTasks(this, taskId)
        enableEdgeToEdge()
        val loaded = runCatching { FinanceStore(this) }
        store = loaded.getOrNull()
        setContent {
            val finance = store
            if (finance == null) {
                BlackSlateTheme {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.safeDrawingPadding().padding(28.dp), verticalArrangement = Arrangement.Center) {
                            Text("Your records are safe", style = MaterialTheme.typography.headlineMedium)
                            Text("Black Slate could not read its saved data. Nothing has been overwritten. Please retain the app data and contact support before trying a restore.")
                        }
                    }
                }
            } else {
                val settings = finance.snapshot.settings
                SideEffect {
                    if (settings.appLock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                BlackSlateTheme(settings.appearance) {
                    if (settings.appLock && !unlocked) {
                        Surface(Modifier.fillMaxSize()) {
                            Column(Modifier.safeDrawingPadding().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("Black Slate", style = MaterialTheme.typography.displayMedium)
                                Text("Your money, kept private.")
                                Button(onClick = { authenticate() }) { Text("Unlock") }
                            }
                        }
                    } else BlackSlateApp(finance, onLockChange = { enabled ->
                        if (enabled) authenticate(true) else finance.updateSettings { it.copy(appLock = false) }
                    })
                }
            }
        }
    }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && store?.snapshot?.settings?.appLock == true) unlocked = false
    }
}

/** A keyguard-safe shortcut: it overlays the lock screen without dismissing it and exposes logging only. */
class LockedLogActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val loaded = runCatching { FinanceStore(this) }
        setContent {
            val finance = loaded.getOrNull()
            if (finance == null) BlackSlateTheme {
                Surface(Modifier.fillMaxSize()) { Text("Black Slate could not read its saved data.", Modifier.padding(28.dp)) }
            } else BlackSlateApp(finance, assistantLogOnly = true)
        }
    }
}

package com.airsoftmodule.charger

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airsoftmodule.charger.data.ChargerViewModel
import com.airsoftmodule.charger.ui.ChargerApp
import com.airsoftmodule.charger.ui.theme.ChargerTheme
import com.airsoftmodule.charger.ui.theme.Tac
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: ChargerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        vm.onUsbIntent(intent)
        Tac.applyAccent(vm.prefs.accent.value)
        lifecycleScope.launch { vm.prefs.accent.state.collect { Tac.applyAccent(it) } }
        setContent {
            val accent by vm.prefs.accent.state.collectAsStateWithLifecycle()
            val keepOn by vm.prefs.keepScreenOn.state.collectAsStateWithLifecycle()
            LaunchedEffect(keepOn) {
                if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            ChargerTheme(accent) { ChargerApp(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.onUsbIntent(intent)
    }
}

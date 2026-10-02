package com.airsoftmodule.charger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airsoftmodule.charger.data.ChargerViewModel
import com.airsoftmodule.charger.data.Link
import com.airsoftmodule.charger.ui.components.Pill
import com.airsoftmodule.charger.ui.components.Toaster
import com.airsoftmodule.charger.ui.screens.ConsoleScreen
import com.airsoftmodule.charger.ui.screens.DashboardScreen
import com.airsoftmodule.charger.ui.screens.GraphsScreen
import com.airsoftmodule.charger.ui.screens.LogScreen
import com.airsoftmodule.charger.ui.screens.SettingsScreen
import com.airsoftmodule.charger.ui.screens.TipsScreen
import com.airsoftmodule.charger.ui.theme.Tac

enum class Tab(val label: String, val icon: ImageVector) {
    DASH("Status", Icons.Rounded.SpaceDashboard),
    GRAPHS("Graphs", Icons.AutoMirrored.Rounded.ShowChart),
    SETTINGS("Settings", Icons.Rounded.Settings),
    TIPS("Tips", Icons.Rounded.Lightbulb),
}

@Composable
fun ChargerApp(vm: ChargerViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.DASH) }
    var console by rememberSaveable { mutableStateOf(false) }
    var logOpen by rememberSaveable { mutableStateOf(false) }
    val link by vm.link.collectAsStateWithLifecycle()

    BackHandler(console) { console = false }
    BackHandler(logOpen) { logOpen = false }
    BackHandler(!console && !logOpen && tab != Tab.DASH) { tab = Tab.DASH }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Tac.Bg,
        contentWindowInsets = WindowInsets(0),
        topBar = { TopBar(link, onClick = { if (link is Link.Connected || link is Link.Demo) Unit else vm.connect() }) },
        bottomBar = {
            if (!console && !logOpen) NavigationBar(containerColor = Tac.Surface, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { Icon(t.icon, t.label) }, label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Tac.Dim, unselectedTextColor = Tac.Dim,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        val glow = MaterialTheme.colorScheme.primary
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(glow.copy(alpha = 0.07f), Tac.Bg), endY = 1400f))
                .padding(pad),
        ) {
            if (console) ConsoleScreen(vm, onBack = { console = false })
            else if (logOpen) LogScreen(vm, onBack = { logOpen = false })
            else AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally { it / 6 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 * dir } + fadeOut())
                },
                label = "tab",
            ) { t ->
                when (t) {
                    Tab.DASH -> DashboardScreen(vm, openTips = { tab = Tab.TIPS }, openLog = { logOpen = true })
                    Tab.GRAPHS -> GraphsScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm, openConsole = { console = true }, openLog = { logOpen = true })
                    Tab.TIPS -> TipsScreen()
                }
            }
        }
    }
    // notifications: top-right corner, below the top bar, hide themselves
    Toaster(vm.messages, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 64.dp, end = 12.dp))
    }
}

@Composable
private fun TopBar(link: Link, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Tac.Bg).statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.onPrimary) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("PD CHARGER", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("3S LiPo · USB-C PD", style = MaterialTheme.typography.bodySmall, color = Tac.Dim)
        }
        Row(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick), horizontalArrangement = Arrangement.End) {
            when (link) {
                is Link.Connected -> Pill("Online", Tac.Ok, icon = Icons.Rounded.Usb)
                Link.Demo -> Pill("Demo", Tac.Warn, icon = Icons.Rounded.Science, pulse = true)
                Link.Searching -> Pill("Waiting for access", Tac.Warn, pulse = true)
                is Link.Error -> Pill("Retry", Tac.Bad, icon = Icons.Rounded.UsbOff)
                Link.Disconnected -> Pill("Offline", Tac.Faint, icon = Icons.Rounded.UsbOff)
            }
        }
    }
}

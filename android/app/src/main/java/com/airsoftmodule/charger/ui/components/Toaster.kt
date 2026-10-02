package com.airsoftmodule.charger.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.airsoftmodule.charger.ui.theme.Tac
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

private class Toast(val id: Long, val text: String) {
    val visible = MutableTransitionState(false).apply { targetState = true }
}

/**
 * Small notifications stacked in the top-right corner. Each one slides in, hides itself after
 * [showMs], and can be tapped away. A message identical to one already shown replaces it.
 */
@Composable
fun Toaster(messages: Flow<String>, modifier: Modifier = Modifier, showMs: Long = 2800, max: Int = 3) {
    val toasts = remember { mutableStateListOf<Toast>() }
    LaunchedEffect(messages) {
        var next = 0L
        messages.collect { msg ->
            toasts.firstOrNull { it.text == msg }?.let { toasts.remove(it) }
            toasts.add(Toast(next++, msg))
            while (toasts.size > max) toasts.removeAt(0)
        }
    }
    Column(modifier.widthIn(max = 280.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (t in toasts) {
            androidx.compose.runtime.key(t.id) {
                LaunchedEffect(t.id) {
                    delay(showMs)
                    t.visible.targetState = false
                }
                // drop it from the list once the exit animation has finished
                LaunchedEffect(t.visible.currentState, t.visible.isIdle) {
                    if (t.visible.isIdle && !t.visible.currentState) toasts.remove(t)
                }
                AnimatedVisibility(
                    visibleState = t.visible,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut(),
                ) {
                    ToastCard(t.text) { t.visible.targetState = false }
                }
            }
        }
    }
}

@Composable
private fun ToastCard(text: String, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val isError = text.startsWith("Module:") || text.startsWith("Not connected")
    val dot = if (isError) Tac.Bad else accent
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .shadow(10.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Tac.Surface3)
            .border(1.dp, dot.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Tac.Text)
    }
}

package com.airsoftmodule.charger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airsoftmodule.charger.data.ChargerViewModel
import com.airsoftmodule.charger.ui.theme.Mono
import com.airsoftmodule.charger.ui.theme.Tac
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConsoleScreen(vm: ChargerViewModel, onBack: () -> Unit) {
    val lines by vm.console.collectAsStateWithLifecycle()
    val logStatus by vm.prefs.logStatus.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.animateScrollToItem(lines.lastIndex) }
    fun submit() { if (input.isNotBlank()) { vm.send(input); input = "" } }

    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Console", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("status", style = MaterialTheme.typography.labelSmall, color = Tac.Dim)
            Spacer(Modifier.width(6.dp))
            Switch(checked = logStatus, onCheckedChange = { vm.prefs.logStatus.set(it) })
            IconButton(onClick = vm::clearConsole) { Icon(Icons.Rounded.DeleteSweep, "Clear", tint = Tac.Dim) }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).background(Tac.Surface, RoundedCornerShape(14.dp)).padding(10.dp),
            state = list,
        ) {
            if (lines.isEmpty()) item { Text("Type a command below, or tap one of the shortcuts.", color = Tac.Faint, fontFamily = Mono, fontSize = 12.sp) }
            items(lines) { l ->
                Row {
                    Text(fmt.format(Date(l.t)) + " ", color = Tac.Faint, fontFamily = Mono, fontSize = 11.sp)
                    Text(
                        (if (l.out) "> " else "") + l.text,
                        color = if (l.out) MaterialTheme.colorScheme.primary else if (l.text.contains("\"err\"")) Tac.Bad else Tac.Text,
                        fontFamily = Mono, fontSize = 11.sp,
                    )
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("s?", "c?", "i?", "mode monitor", "mode charge", "set led 100", "save").forEach { c ->
                AssistChip(
                    onClick = { vm.send(c) }, label = { Text(c, fontFamily = Mono) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Tac.Surface2, labelColor = Tac.Text),
                )
            }
        }
        Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text("command", fontFamily = Mono) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Tac.Outline, unfocusedContainerColor = Tac.Surface, focusedContainerColor = Tac.Surface),
            )
            IconButton(onClick = ::submit) { Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

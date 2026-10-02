package com.airsoftmodule.charger.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.airsoftmodule.charger.data.Tip
import com.airsoftmodule.charger.data.TipCategory
import com.airsoftmodule.charger.data.Tips
import com.airsoftmodule.charger.ui.components.Panel
import com.airsoftmodule.charger.ui.theme.Tac

private fun TipCategory.icon(): ImageVector = when (this) {
    TipCategory.SAFETY -> Icons.Rounded.Shield
    TipCategory.CHARGING -> Icons.Rounded.BatteryChargingFull
    TipCategory.STORAGE -> Icons.Rounded.Inventory2
    TipCategory.FIELD -> Icons.Rounded.Backpack
    TipCategory.MODULE -> Icons.Rounded.Memory
    TipCategory.GEAR -> Icons.Rounded.Build
}

private fun TipCategory.color(): Color = when (this) {
    TipCategory.SAFETY -> Tac.Bad
    TipCategory.CHARGING -> Tac.Ok
    TipCategory.STORAGE -> Tac.Cell[0]
    TipCategory.FIELD -> Tac.Warn
    TipCategory.MODULE -> Tac.Cell[2]
    TipCategory.GEAR -> Color(0xFFD9B27C)
}

@Composable
fun TipsScreen() {
    var filter by rememberSaveable { mutableStateOf<TipCategory?>(null) }
    val list = remember(filter) { Tips.all.filter { filter == null || it.category == filter } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Panel(Modifier.fillMaxWidth()) {
                Text("LiPo field manual", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${Tips.all.size} short tips to keep your packs healthy, safe and ready for game day. Tap a tip to expand it.",
                    style = MaterialTheme.typography.bodyMedium, color = Tac.Dim,
                )
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = filter == null, onClick = { filter = null }, label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Tac.Surface3, selectedLabelColor = Tac.Text, containerColor = Tac.Bg, labelColor = Tac.Dim),
                    )
                }
                items(TipCategory.entries) { c ->
                    FilterChip(
                        selected = filter == c, onClick = { filter = if (filter == c) null else c }, label = { Text(c.title) },
                        leadingIcon = { Icon(c.icon(), null, Modifier.size(16.dp), tint = c.color()) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.color().copy(alpha = 0.16f), selectedLabelColor = c.color(), containerColor = Tac.Bg, labelColor = Tac.Dim),
                    )
                }
            }
        }
        items(list, key = { it.title }) { TipCard(it) }
    }
}

@Composable
private fun TipCard(tip: Tip) {
    var open by rememberSaveable(tip.title) { mutableStateOf(false) }
    val c = tip.category.color()
    Panel(Modifier.fillMaxWidth().animateContentSize().clickable { open = !open }, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(c.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(tip.category.icon(), null, tint = c, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tip.category.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = c)
                Text(tip.title, style = MaterialTheme.typography.titleSmall)
            }
            Icon(Icons.Rounded.ExpandMore, null, tint = Tac.Faint, modifier = Modifier.rotate(if (open) 180f else 0f))
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            Text(tip.body, style = MaterialTheme.typography.bodyMedium, color = Tac.Dim, modifier = Modifier.padding(start = 48.dp))
        }
    }
}

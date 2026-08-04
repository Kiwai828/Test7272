package com.recapmaker.app.ui.history

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.recapmaker.app.ui.common.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(vm: HistoryViewModel = hiltViewModel(), onBack: () -> Unit) {
    val s = vm.state
    val ctx = LocalContext.current

    LaunchedEffect(Unit) { vm.loadAll() }

    Column(Modifier.fillMaxSize().background(DarkBg)) {
        Surface(color = CardBg, shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) }
                Text("Processing History", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Purple, modifier = Modifier.weight(1f))
            }
        }

        AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = tween(400)) + slideInVertically(animationSpec = tween(400), initialOffsetY = { it / 4 }), exit = fadeOut()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                val stats = vm.getStats()
                SummaryStatsCard(stats)

                FilterBar(
                    filterStatus = s.filterStatus,
                    onStatusChange = { vm.onFilterStatusChange(it) },
                    filterEffect = s.filterEffect,
                    effects = vm.effectsList.value,
                    onEffectChange = { vm.onFilterEffectChange(it) },
                    query = s.filterQuery,
                    onQueryChange = { vm.onQueryChange(it) },
                )

                if (s.isLoading) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Purple, strokeWidth = 3.dp)
                    }
                } else if (s.filteredEntries.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, null, tint = TextDim, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No history yet", color = TextDim, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 600.dp)) {
                        items(s.filteredEntries, key = { it.id }) { entry ->
                            HistoryEntryCard(entry = entry, onClick = { vm.showDetail(entry) })
                        }
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
        }
    }

    if (s.showDetail && s.selectedEntry != null) {
        HistoryDetailDialog(
            entry = s.selectedEntry,
            onDismiss = vm::hideDetail,
            onDelete = { vm.deleteEntry(s.selectedEntry!!) },
            onReprocess = { vm.reprocessEntry(s.selectedEntry!!) },
        )
    }
}

@Composable
private fun SummaryStatsCard(stats: HistoryViewModel.HistoryStats) {
    Surface(color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Summary", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("Total", "${stats.total}", Purple, Modifier.weight(1f))
                StatItem("Success", "${stats.succeeded}", Emerald, Modifier.weight(1f))
                StatItem("Failed", "${stats.failed}", ErrorRed, Modifier.weight(1f))
                StatItem("Coins", "${stats.totalCoins}", Gold, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("Time Saved", formatTimeShort(stats.totalTimeMs), Cyan, Modifier.weight(1f))
                StatItem("Success Rate", "${"%.0f".format(stats.successRate)}%", if (stats.successRate >= 80f) Emerald else WarningYellow, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = TextDim, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FilterBar(
    filterStatus: String,
    onStatusChange: (String) -> Unit,
    filterEffect: String,
    effects: List<String>,
    onEffectChange: (String) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Surface(color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Search, null, tint = TextDim, modifier = Modifier.size(18.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search...", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple),
                    shape = RoundedCornerShape(10.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Status:", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                listOf("all" to "All", "completed" to "Success", "failed" to "Failed").forEach { (v, l) ->
                    val sel = filterStatus == v
                    Surface(modifier = Modifier.clickable { onStatusChange(v) }, color = if (sel) Purple.copy(0.15f) else SurfaceDark.copy(0.65f),
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, if (sel) Purple.copy(0.4f) else CardBorder)) {
                        Text(l, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.White else TextMid)
                    }
                }
            }

            if (effects.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Effects:", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            val sel = filterEffect.isEmpty()
                            Surface(modifier = Modifier.clickable { onEffectChange("") }, color = if (sel) Purple.copy(0.15f) else SurfaceDark.copy(0.65f),
                                shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, if (sel) Purple.copy(0.4f) else CardBorder)) {
                                Text("All", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.White else TextMid)
                            }
                        }
                        items(effects) { effect ->
                            val sel = filterEffect == effect
                            Surface(modifier = Modifier.clickable { onEffectChange(if (sel) "" else effect) }, color = if (sel) Purple.copy(0.15f) else SurfaceDark.copy(0.65f),
                                shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, if (sel) Purple.copy(0.4f) else CardBorder)) {
                                Text(effect.replace("_", " "), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.White else TextMid)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(entry: VideoHistoryEntity, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable { onClick() }, color = CardBg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, CardBorder)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceDark), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = Purple, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.inputVideoName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.outputVideoName.isNotBlank()) Text(entry.outputVideoName, color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sc = if (entry.status == "completed") Emerald else ErrorRed
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(sc))
                    Text(entry.status, color = sc, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(formatDate(entry.createdAt), color = TextDim, fontSize = 11.sp)
                    Text(formatDurationShort(entry.duration), color = TextDim, fontSize = 11.sp)
                    if (entry.coinsSpent > 0) Text("${entry.coinsSpent} coins", color = Gold, fontSize = 11.sp)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextDim, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun HistoryDetailDialog(entry: VideoHistoryEntity, onDismiss: () -> Unit, onDelete: () -> Unit, onReprocess: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = CardBg, border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Details", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, null, tint = TextDim, modifier = Modifier.size(18.dp)) }
                }

                val sc = if (entry.status == "completed") Emerald else ErrorRed
                Surface(color = sc.copy(0.1f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, sc.copy(0.25f))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (entry.status == "completed") Icons.Default.CheckCircle else Icons.Default.Error, null, tint = sc, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (entry.status == "completed") "Success" else "Failed", color = sc, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }

                SectionCard("Input / Output", icon = Icons.Default.VideoFile, iconColor = Purple) {
                    DetailRow("Input", entry.inputVideoName)
                    if (entry.outputVideoName.isNotBlank()) DetailRow("Output", entry.outputVideoName)
                }

                SectionCard("Settings", icon = Icons.Default.Tune, iconColor = Purple) {
                    DetailRow("Duration", formatDuration(entry.duration))
                    DetailRow("File Size", formatFileSize(entry.fileSize))
                    DetailRow("Processing Time", "${entry.processingTimeMs / 1000}s")
                    DetailRow("Coins Spent", "${entry.coinsSpent}")
                    DetailRow("TTS Used", if (entry.ttsUsed) "Yes" else "No")
                    DetailRow("Subtitles", if (entry.subtitleGenerated) "Yes" else "No")
                }

                val effects = parseEffects(entry.effectsApplied)
                if (effects.isNotEmpty()) {
                    SectionCard("Effects Applied", icon = Icons.Default.AutoAwesome, iconColor = Emerald) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(effects) { effect ->
                                Surface(color = Emerald.copy(0.08f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Emerald.copy(0.2f))) {
                                    Text(effect.replace("_", " "), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, color = Emerald, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                if (entry.errorMessage.isNotBlank()) {
                    SectionCard("Error", icon = Icons.Default.Error, iconColor = ErrorRed) {
                        Text(entry.errorMessage, color = ErrorRed, fontSize = 13.sp)
                    }
                }

                SectionCard("Date", icon = Icons.Default.Schedule, iconColor = Gold) {
                    DetailRow("Processed", formatDateFull(entry.createdAt))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(modifier = Modifier.weight(1f).clickable { onReprocess(); Toast.makeText(ctx, "Opening editor...", Toast.LENGTH_SHORT).show() }, color = Purple.copy(0.1f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Purple.copy(0.3f))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Replay, null, tint = Purple, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Reprocess", color = Purple, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                    Surface(modifier = Modifier.weight(1f).clickable { onDelete(); Toast.makeText(ctx, "Deleted", Toast.LENGTH_SHORT).show() }, color = ErrorRed.copy(0.08f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ErrorRed.copy(0.25f))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Delete", color = ErrorRed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = TextDim, fontSize = 13.sp, modifier = Modifier.width(100.dp))
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatDate(timestamp: Long): String {
    val fmt = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return fmt.format(Date(timestamp))
}

private fun formatDateFull(timestamp: Long): String {
    val fmt = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm:ss", Locale.getDefault())
    return fmt.format(Date(timestamp))
}

private fun formatDurationShort(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "${m}m ${s}s"
}

private fun formatTimeShort(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
}

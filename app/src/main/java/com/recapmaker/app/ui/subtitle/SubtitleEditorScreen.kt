package com.recapmaker.app.ui.subtitle

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.recapmaker.app.ui.common.*
import kotlinx.coroutines.launch

@Composable
fun SubtitleEditorScreen(onBack: () -> Unit, vm: SubtitleEditorViewModel = hiltViewModel()) {
    val s = vm.state; val ctx = LocalContext.current
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.onVideoSelected(it, ctx) } }
    val srtPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.importSrt(it, ctx) } }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(animationSpec = tween(400)) + slideInVertically(animationSpec = tween(400), initialOffsetY = { it / 4 }), exit = fadeOut()) {
            Column(Modifier.fillMaxSize().background(DarkBg)) {
                Surface(color = CardBg, shadowElevation = 4.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) }
                        Text("Subtitle Editor", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald, modifier = Modifier.weight(1f))
                        CoinBadge(s.gold, s.silver)
                    }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // ═══ VIDEO SOURCE ═══
                    SectionCard("Video File", Icons.Default.VideoFile, Emerald) {
                        OutlinedButton(onClick = { videoPicker.launch("video/*") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (s.videoUri != null) Emerald else CardBorder)) {
                            Icon(if (s.videoUri != null) Icons.Default.CheckCircle else Icons.Default.CloudUpload, null, tint = if (s.videoUri != null) Emerald else TextDim)
                            Spacer(Modifier.width(8.dp)); Text(if (s.videoUri != null) "Video selected" else "Select video", color = if (s.videoUri != null) Emerald else TextDim)
                        }
                        if (s.videoUri != null && s.videoLocalPath != null) {
                            Spacer(Modifier.height(8.dp))
                            VideoPreviewPlayer(uri = s.videoUri!!) {}
                            if (s.videoDurationMs > 0) {
                                Text("Duration: ${s.videoDurationMs / 1000 / 60}:${"%02d".format((s.videoDurationMs / 1000) % 60)}", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }

                    // ═══ IMPORT / EXPORT ═══
                    SectionCard("SRT File", Icons.Default.Subtitles, Purple) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { srtPicker.launch("text/*") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Purple.copy(0.3f))) {
                                Icon(Icons.Default.FileUpload, null, tint = Purple, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp)); Text("Import SRT", color = Purple, fontSize = 13.sp)
                            }
                            OutlinedButton(onClick = { vm.exportSrt(ctx) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Emerald.copy(0.3f)), enabled = s.subtitles.isNotEmpty()) {
                                Icon(Icons.Default.FileDownload, null, tint = Emerald, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp)); Text("Export SRT", color = Emerald, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.burnToVideo(ctx) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (s.subtitles.isNotEmpty()) Gold.copy(0.4f) else CardBorder), enabled = s.subtitles.isNotEmpty() && s.videoLocalPath != null && !s.isProcessing) {
                            Icon(Icons.Default.Whatshot, null, tint = if (s.subtitles.isNotEmpty()) Gold else TextDim, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp)); Text("Burn into Video", color = if (s.subtitles.isNotEmpty()) Gold else TextDim, fontSize = 13.sp)
                        }
                    }

                    // ═══ VISUAL TIMELINE ═══
                    if (s.subtitles.isNotEmpty() && s.videoDurationMs > 0) {
                        SectionCard("Visual Timeline", Icons.Default.Timeline, Cyan) {
                            SubtitleTimeline(
                                subtitles = s.subtitles,
                                durationMs = s.videoDurationMs,
                                selectedIndex = s.selectedIndex,
                                onSelect = { vm.selectEntry(it) },
                                onTimeChange = { index, startMs, endMs -> vm.updateEntryTime(index, startMs, endMs) },
                            )
                        }
                    }

                    // ═══ SUBTITLE LIST ═══
                    if (s.subtitles.isNotEmpty()) {
                        SectionCard("Subtitle Lines (${s.subtitles.size})", Icons.Default.List, Purple) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                itemsIndexed(s.subtitles, key = { _, entry -> entry.index }) { index, entry ->
                                    val isSelected = s.selectedIndex == index
                                    Surface(
                                        color = if (isSelected) Purple.copy(0.1f) else SurfaceDark,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, if (isSelected) Purple.copy(0.4f) else CardBorder),
                                        onClick = { vm.selectEntry(index) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Surface(color = Purple, shape = RoundedCornerShape(8.dp)) {
                                                    Text("#${entry.index}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                                }
                                                Spacer(Modifier.weight(1f))
                                                Text("${FFmpegProcessor.formatSrtTime(entry.startMs)} --> ${FFmpegProcessor.formatSrtTime(entry.endMs)}", color = TextDim, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = entry.text,
                                                onValueChange = { vm.updateEntryText(index, it) },
                                                modifier = Modifier.fillMaxWidth(),
                                                minLines = 2,
                                                maxLines = 4,
                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple),
                                                shape = RoundedCornerShape(10.dp),
                                                placeholder = { Text("Subtitle text...", fontSize = 13.sp) },
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                OutlinedButton(onClick = { vm.splitEntry(index, (entry.startMs + entry.endMs) / 2) }, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Cyan.copy(0.3f)), modifier = Modifier.height(32.dp)) {
                                                    Icon(Icons.Default.CallSplit, null, modifier = Modifier.size(14.dp), tint = Cyan); Spacer(Modifier.width(4.dp)); Text("Split", fontSize = 11.sp, color = Cyan)
                                                }
                                                if (index < s.subtitles.lastIndex) {
                                                    OutlinedButton(onClick = { vm.mergeEntries(index) }, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, WarningYellow.copy(0.3f)), modifier = Modifier.height(32.dp)) {
                                                        Icon(Icons.Default.CallMerge, null, modifier = Modifier.size(14.dp), tint = WarningYellow); Spacer(Modifier.width(4.dp)); Text("Merge Next", fontSize = 11.sp, color = WarningYellow)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (s.videoLocalPath != null) {
                        Surface(color = SurfaceDark, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
                            Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Subtitles, null, modifier = Modifier.size(48.dp), tint = TextDim)
                                Spacer(Modifier.height(12.dp))
                                Text("Import an SRT file to start editing", color = TextDim, fontSize = 14.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        s.error?.let { msg -> Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(), color = ErrorRed.copy(0.9f), shape = RoundedCornerShape(12.dp)) { Row(Modifier.padding(14.dp)) { Text(msg, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f)); IconButton(onClick = { vm.clearError() }) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp)) } } }; LaunchedEffect(msg) { kotlinx.coroutines.delay(4000); vm.clearError() } }
        s.success?.let { msg -> Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(), color = Emerald.copy(0.9f), shape = RoundedCornerShape(12.dp)) { Text(msg, color = DarkBg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(14.dp)) }; LaunchedEffect(msg) { kotlinx.coroutines.delay(5000); vm.clearSuccess() } }
    }
}

@Composable
private fun SubtitleTimeline(
    subtitles: List<SubtitleEntry>,
    durationMs: Long,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onTimeChange: (Int, Long?, Long?) -> Unit,
) {
    if (durationMs <= 0) return
    val colors = listOf(Purple, Emerald, Gold, Cyan, Rose, WarningYellow)
    var containerWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                .onSizeChanged { containerWidth = it.width },
        ) {
            if (containerWidth > 0) {
                subtitles.forEachIndexed { index, entry ->
                    val leftFrac = (entry.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    val rightFrac = (entry.endMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    val leftPx = (leftFrac * containerWidth).roundToInt()
                    val rightPx = (rightFrac * containerWidth).roundToInt()
                    val widthPx = (rightPx - leftPx).coerceAtLeast(4)

                    val isSelected = selectedIndex == index
                    val blockColor = if (isSelected) colors[index % colors.size] else colors[index % colors.size].copy(alpha = 0.5f)

                    TimelineBlock(
                        leftPx = leftPx,
                        widthPx = widthPx,
                        color = blockColor,
                        label = "#${entry.index}",
                        selected = isSelected,
                        onSelect = { onSelect(index) },
                        onDragLeft = { drag ->
                            val newStart = (entry.startMs + drag).coerceIn(0, entry.endMs - 500)
                            onTimeChange(index, newStart, null)
                        },
                        onDragRight = { drag ->
                            val newEnd = (entry.endMs + drag).coerceIn(entry.startMs + 500, durationMs)
                            onTimeChange(index, null, newEnd)
                        },
                        onDragWhole = { drag ->
                            val dur = entry.endMs - entry.startMs
                            var newStart = entry.startMs + drag
                            var newEnd = entry.endMs + drag
                            if (newStart < 0) { newEnd -= newStart; newStart = 0 }
                            if (newEnd > durationMs) { newStart -= (newEnd - durationMs); newEnd = durationMs }
                            newStart = newStart.coerceIn(0, (durationMs - dur).coerceAtLeast(0))
                            newEnd = newStart + dur
                            onTimeChange(index, newStart, newEnd)
                        },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0:00", color = TextDim, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text("${durationMs / 1000 / 60}:${"%02d".format((durationMs / 1000) % 60)}", color = TextDim, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}

@Composable
private fun TimelineBlock(
    leftPx: Int,
    widthPx: Int,
    color: Color,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onDragLeft: (Long) -> Unit,
    onDragRight: (Long) -> Unit,
    onDragWhole: (Long) -> Unit,
) {
    var dragMode by remember { mutableStateOf<String?>(null) }
    var lastDragX by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .offset { IntOffset(leftPx, 0) }
            .width(with(LocalDensity.current) { widthPx.toDp() })
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White.copy(0.6f) else Color.White.copy(0.2f), RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { lastDragX = it.x; dragMode = when { it.x < 12 -> "left"; it.x > widthPx - 12 -> "right"; else -> "whole" } },
                    onDrag = { change, dragAmount ->
                        val dx = dragAmount.x
                        when (dragMode) {
                            "left" -> onDragLeft(dx.toLong())
                            "right" -> onDragRight(dx.toLong())
                            "whole" -> { lastDragX += dx; onDragWhole(dx.toLong()) }
                        }
                        change.consume()
                    },
                    onDragEnd = { dragMode = null },
                )
            }
            .clickable { onSelect() },
    ) {
        if (widthPx > 24) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
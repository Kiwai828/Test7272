package com.recapmaker.app.ui.subtitle

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.recapmaker.app.ui.common.*

@Composable
fun SubtitleScreen(onBack: () -> Unit, vm: SubtitleViewModel = hiltViewModel()) {
    val s = vm.state; val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.onVideoSelected(it, ctx) } }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(animationSpec = tween(400)) + slideInVertically(animationSpec = tween(400), initialOffset = { it / 4 }), exit = fadeOut()) {
            Column(Modifier.fillMaxSize().background(DarkBg)) {
            Surface(color = CardBg, shadowElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) }
                    Text("Subtitle Generator", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald, modifier = Modifier.weight(1f))
                    CoinBadge(s.gold, s.silver)
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionCard("Video File", Icons.Default.VideoFile, Emerald) {
                    OutlinedButton(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (s.videoUri != null) Emerald else CardBorder)) {
                        Icon(if (s.videoUri != null) Icons.Default.CheckCircle else Icons.Default.CloudUpload, null, tint = if (s.videoUri != null) Emerald else TextDim)
                        Spacer(Modifier.width(8.dp)); Text(if (s.videoUri != null) "Video ရွေးပြီး" else "Device မှ ရွေးရန်", color = if (s.videoUri != null) Emerald else TextDim)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = CardBorder); Text("  OR  ", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold); HorizontalDivider(Modifier.weight(1f), color = CardBorder)
                    }
                    Text("YouTube / TikTok / Facebook / direct URL", color = TextDim, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = s.urlInput, onValueChange = { vm.updateUrl(it) }, modifier = Modifier.weight(1f),
                            placeholder = { Text("URL paste...", fontSize = 13.sp) }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Emerald, unfocusedBorderColor = CardBorder, cursorColor = Emerald),
                            shape = RoundedCornerShape(12.dp, 0.dp, 0.dp, 12.dp))
                        Button(onClick = { vm.checkUrlInfo(ctx) }, enabled = s.urlInput.isNotBlank() && !s.isDownloading && !s.isCheckingUrl,
                            shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp), modifier = Modifier.height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald)) {
                            if (s.isDownloading || s.isCheckingUrl) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.Download, null, tint = DarkBg)
                        }
                    }
                    AnimatedVisibility(s.isDownloading) {
                        Column(Modifier.padding(top = 8.dp)) {
                            LinearProgressIndicator(progress = { s.downloadProgress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = Emerald, trackColor = CardBorder)
                            Text("Downloading... ${(s.downloadProgress * 100).toInt()}%", color = TextDim, fontSize = 11.sp)
                        }
                    }
                    if (s.videoUri != null) { Spacer(Modifier.height(8.dp)); VideoPreviewPlayer(uri = s.videoUri!!) {} }
                }

                SectionCard("Subtitle Style", Icons.Default.FormatSize, Purple) {
                    Text("Font Size: ${s.fontSize.toInt()}", color = TextDim, fontSize = 13.sp)
                    Slider(value = s.fontSize, onValueChange = { vm.setFontSize(it) }, valueRange = 10f..32f, colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple))
                    EffectToggle("Background Box", Icons.Default.CheckBoxOutlineBlank, s.boxEnabled) { vm.toggleBox(it) }
                    Spacer(Modifier.height(8.dp)); Text("Position", color = TextDim, fontSize = 13.sp)
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("top_center" to "Top", "bottom_center" to "Bottom").forEach { (v, l) ->
                            FilterChip(selected = s.position == v, onClick = { vm.setPosition(v) }, label = { Text(l, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Purple, selectedLabelColor = TextPrimary))
                        }
                    }
                }

                PrimaryButton(text = if (s.isProcessing) "Generating..." else "Generate Subtitles", onClick = { vm.startProcessing(ctx) },
                    loading = s.isProcessing, color = Emerald, enabled = s.videoLocalPath != null)
                Spacer(Modifier.height(24.dp))
            }
        }
        s.error?.let { msg -> Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(), color = ErrorRed.copy(0.9f), shape = RoundedCornerShape(12.dp)) { Row(Modifier.padding(14.dp)) { Text(msg, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f)); IconButton(onClick = { vm.clearError() }) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp)) } } }; LaunchedEffect(msg) { kotlinx.coroutines.delay(4000); vm.clearError() } }
    }

    // ═══ RESOLUTION POPUP ═══
    if (s.showResolutionPopup && s.videoInfo != null) {
        Dialog(onDismissRequest = { vm.dismissResolutionPopup() }) {
            Surface(shape = RoundedCornerShape(20.dp), color = CardBg, border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VideoFile, null, tint = Emerald, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.videoInfo!!.title.take(60), fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp, maxLines = 2)
                            if (s.videoInfo!!.duration > 0) Text("${s.videoInfo!!.duration / 60}:${"%02d".format(s.videoInfo!!.duration % 60)}", color = TextDim, fontSize = 12.sp)
                        }
                        IconButton(onClick = { vm.dismissResolutionPopup() }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, null, tint = TextDim, modifier = Modifier.size(18.dp)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Resolution ရွေးပါ", color = TextDim, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(s.videoInfo!!.formats) { fmt ->
                            Surface(onClick = { vm.downloadWithFormat(ctx, fmt) }, color = if (fmt.formatId == "best") Emerald.copy(0.15f) else SurfaceDark,
                                shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (fmt.formatId == "best") Emerald.copy(0.3f) else CardBorder)) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (fmt.formatId == "best") Icons.Default.AutoAwesome else Icons.Default.HighQuality, null, tint = if (fmt.formatId == "best") Emerald else Purple, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(fmt.resolution, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                                        if (fmt.note.isNotBlank()) Text(fmt.note, color = TextDim, fontSize = 11.sp)
                                    }
                                    if (fmt.fileSize > 0) Text("${"%.1f".format(fmt.fileSize / (1024.0 * 1024.0))}MB", color = TextDim, fontSize = 11.sp)
                                    Spacer(Modifier.width(8.dp)); Icon(Icons.Default.Download, null, tint = TextDim, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

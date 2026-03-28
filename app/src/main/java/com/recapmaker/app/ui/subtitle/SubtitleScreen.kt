package com.recapmaker.app.ui.subtitle

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.recapmaker.app.ui.common.*

@Composable
fun SubtitleScreen(onBack: () -> Unit, vm: SubtitleViewModel = hiltViewModel()) {
    val s = vm.state; val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.onVideoSelected(it, ctx) } }

    Box(Modifier.fillMaxSize()) {
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
                    Text("Direct video URL (mp4) ထည့်ပါ", color = TextDim, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = s.urlInput, onValueChange = { vm.updateUrl(it) }, modifier = Modifier.weight(1f),
                            placeholder = { Text("https://...mp4", fontSize = 13.sp) }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Emerald, unfocusedBorderColor = CardBorder, cursorColor = Emerald),
                            shape = RoundedCornerShape(12.dp, 0.dp, 0.dp, 12.dp))
                        Button(onClick = { vm.downloadFromUrl(ctx) }, enabled = s.urlInput.isNotBlank() && !s.isDownloading,
                            shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp), modifier = Modifier.height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald)) {
                            if (s.isDownloading) CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.Download, null, tint = DarkBg)
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
                        listOf("top_center" to "Top", "middle" to "Middle", "bottom_center" to "Bottom").forEach { (v, l) ->
                            FilterChip(selected = s.position == v, onClick = { vm.setPosition(v) }, label = { Text(l, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Purple, selectedLabelColor = TextPrimary))
                        }
                    }
                    Spacer(Modifier.height(8.dp)); Text("Font Color", color = TextDim, fontSize = 13.sp); Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("#FFFFFF", "#FFFF00", "#00FF00", "#00FFFF", "#FF0000", "#FF69B4").forEach { hex ->
                            val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.White }
                            Box(Modifier.size(30.dp).background(c, RoundedCornerShape(8.dp)).border(2.dp, if (s.fontColor.equals(hex, true)) Purple else Color.Transparent, RoundedCornerShape(8.dp)).clickable { vm.setFontColor(hex) })
                        }
                    }
                }

                SectionCard("Effect များ", Icons.Default.Tune, Purple) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EffectToggle("Flip", Icons.Default.SwapHoriz, s.flipEnabled) { vm.toggleFlip(it) }
                        EffectToggle("Speed", Icons.Default.Speed, s.speedEnabled) { vm.toggleSpeed(it) }
                        EffectToggle("Noise", Icons.Default.Grain, s.noiseEnabled) { vm.toggleNoise(it) }
                        EffectToggle("Blur", Icons.Default.BlurOn, s.blurEnabled, switchColor = Rose) { vm.toggleBlur(it) }
                    }
                }

                PrimaryButton(text = if (s.isProcessing) "Generating..." else "Generate Subtitles", onClick = { vm.startProcessing(ctx) },
                    loading = s.isProcessing, color = Emerald, enabled = s.videoLocalPath != null)
                Spacer(Modifier.height(24.dp))
            }
        }
        s.error?.let { msg -> Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(), color = ErrorRed.copy(0.9f), shape = RoundedCornerShape(12.dp)) { Row(Modifier.padding(14.dp)) { Text(msg, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f)); IconButton(onClick = { vm.clearError() }) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp)) } } }; LaunchedEffect(msg) { kotlinx.coroutines.delay(4000); vm.clearError() } }
    }
}

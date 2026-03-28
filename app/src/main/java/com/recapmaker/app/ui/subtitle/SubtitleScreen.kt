package com.recapmaker.app.ui.subtitle

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recapmaker.app.ui.common.*

@Composable
fun SubtitleScreen(onBack: () -> Unit) {
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var fontColor by remember { mutableStateOf("#FFFFFF") }
    var fontSize by remember { mutableStateOf(16f) }
    var boxEnabled by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf("bottom_center") }
    var isProcessing by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> videoUri = uri }

    Column(Modifier.fillMaxSize().background(DarkBg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) }
            Text("Subtitle Generator", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Upload
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { picker.launch("video/*") },
                color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (videoUri != null) Emerald else CardBorder),
            ) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (videoUri != null) Icons.Default.CheckCircle else Icons.Default.Subtitles, null,
                        tint = if (videoUri != null) Emerald else TextDim, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (videoUri != null) "Video selected" else "Tap to select video", color = if (videoUri != null) Emerald else TextDim)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Subtitle styling
            Text("Subtitle Style", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))

            Text("Font Size: ${fontSize.toInt()}", color = TextDim, fontSize = 13.sp)
            Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 10f..32f,
                colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple))

            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Background Box", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(checked = boxEnabled, onCheckedChange = { boxEnabled = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Purple))
            }

            // Position selector
            Text("Position", color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                listOf("top_center" to "Top", "middle" to "Middle", "bottom_center" to "Bottom").forEach { (value, label) ->
                    FilterChip(
                        selected = position == value, onClick = { position = value },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Purple, selectedLabelColor = TextPrimary),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Process
            PrimaryButton(
                text = if (isProcessing) "Generating..." else "Generate Subtitles",
                onClick = {
                    if (videoUri != null) {
                        isProcessing = true
                        // TODO: Extract audio → Groq STT → generate SRT → FFmpegX burn
                    }
                },
                loading = isProcessing, color = Emerald,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

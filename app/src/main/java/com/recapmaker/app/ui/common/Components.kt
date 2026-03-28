package com.recapmaker.app.ui.common

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.recapmaker.app.data.model.BlurArea
import com.recapmaker.app.data.model.VoiceGender
import kotlin.math.roundToInt

// ══════════════════════════════════
// TEXT FIELDS
// ══════════════════════════════════

@Composable
fun AppTextField(
    value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = imeAction),
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        singleLine = true, modifier = modifier.fillMaxWidth(),
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions { onImeAction() },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, focusedLabelColor = Purple, cursorColor = Purple),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun PasswordField(
    value: String, onValueChange: (String) -> Unit, label: String = "Password",
    modifier: Modifier = Modifier, imeAction: ImeAction = ImeAction.Done, onImeAction: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        singleLine = true, modifier = modifier.fillMaxWidth(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TextDim) } },
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions { onImeAction() },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, focusedLabelColor = Purple, cursorColor = Purple),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, loading: Boolean = false, modifier: Modifier = Modifier, color: Color = Purple, enabled: Boolean = true) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth().height(50.dp), enabled = !loading && enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = color.copy(0.4f)), shape = RoundedCornerShape(12.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), Color.White, strokeWidth = 2.dp)
        else Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ErrorBanner(message: String?) {
    if (message != null) {
        Surface(color = ErrorRed.copy(0.15f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(message, color = ErrorRed, fontSize = 13.sp, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun CoinBadge(gold: Int, silver: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = Gold.copy(0.1f), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Gold.copy(0.25f))) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🥇", fontSize = 12.sp); Spacer(Modifier.width(3.dp)); Text("$gold", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Surface(color = SilverColor.copy(0.1f), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, SilverColor.copy(0.25f))) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🥈", fontSize = 12.sp); Spacer(Modifier.width(3.dp)); Text("$silver", color = SilverColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// ══════════════════════════════════
// SECTION CARD
// ══════════════════════════════════

@Composable
fun SectionCard(title: String, icon: ImageVector? = null, iconColor: Color = Purple, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder)) {
        Column(Modifier.padding(16.dp)) {
            if (title.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
                    Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

// ══════════════════════════════════
// EFFECT TOGGLE
// ══════════════════════════════════

@Composable
fun EffectToggle(label: String, icon: ImageVector, checked: Boolean, switchColor: Color = Purple, onToggle: (Boolean) -> Unit) {
    Surface(color = if (checked) switchColor.copy(0.06f) else Color.Transparent, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (checked) switchColor.copy(0.2f) else Purple.copy(0.07f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (checked) switchColor else TextDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onToggle, colors = SwitchDefaults.colors(
                checkedTrackColor = switchColor, checkedThumbColor = Color.White, uncheckedTrackColor = SurfaceDark, uncheckedBorderColor = CardBorder))
        }
    }
}

// ══════════════════════════════════
// VOICE CARD & TAB
// ══════════════════════════════════

@Composable
fun VoiceCard(name: String, label: String, gender: VoiceGender, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val gc = when (gender) { VoiceGender.Male -> Cyan; VoiceGender.Female -> Rose; VoiceGender.Neutral -> Purple }
    Surface(modifier = modifier.clickable { onClick() }, color = if (isSelected) Purple.copy(0.08f) else CardBg.copy(0.65f),
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (isSelected) Purple.copy(0.4f) else Purple.copy(0.07f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(gc.copy(0.1f)).border(1.dp, gc.copy(0.18f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Text(label.take(2).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = gc)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(gender.name, fontSize = 10.sp, color = gc.copy(0.8f))
            }
            if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = Purple, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun VoiceTabRow(tabs: List<Pair<String, String>>, selectedTab: String, onTabSelected: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEach { (id, label) ->
            val sel = selectedTab == id
            Surface(modifier = Modifier.weight(1f).clickable { onTabSelected(id) }, color = if (sel) Purple.copy(0.12f) else SurfaceDark.copy(0.65f),
                shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, if (sel) Purple.copy(0.35f) else Purple.copy(0.12f))) {
                Text(label, modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(), textAlign = TextAlign.Center,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.White else TextMid)
            }
        }
    }
}

// ══════════════════════════════════
// POSITION SELECTOR
// ══════════════════════════════════

@Composable
fun PositionSelector(selected: String, onSelect: (String) -> Unit) {
    val pos = listOf("top_left" to "↖", "top_center" to "↑", "top_right" to "↗", "bottom_left" to "↙", "bottom_center" to "↓", "bottom_right" to "↘", "center" to "◎")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { pos.take(3).forEach { (v, l) -> PosChip(l, selected == v, Modifier.weight(1f)) { onSelect(v) } } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { PosChip(pos[6].second, selected == "center", Modifier.fillMaxWidth(0.33f)) { onSelect("center") } }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { pos.subList(3, 6).forEach { (v, l) -> PosChip(l, selected == v, Modifier.weight(1f)) { onSelect(v) } } }
    }
}

@Composable
private fun PosChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable { onClick() }, color = if (selected) Purple.copy(0.15f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, if (selected) Purple.copy(0.4f) else CardBorder)) {
        Text(label, modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth(), textAlign = TextAlign.Center,
            fontSize = 14.sp, color = if (selected) Purple else TextDim, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

// ══════════════════════════════════
// VIDEO PREVIEW (dynamic aspect ratio)
// ══════════════════════════════════

@Composable
fun VideoPreviewPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    overlayContent: @Composable BoxScope.(containerSize: IntSize) -> Unit = {},
) {
    val context = LocalContext.current
    var videoRatio by remember { mutableFloatStateOf(16f / 9f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    DisposableEffect(uri) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    Box(
        modifier = modifier.fillMaxWidth()
            .aspectRatio(videoRatio, matchHeightConstraintsFirst = false)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
    ) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = true } },
            modifier = Modifier.fillMaxSize(),
        )
        overlayContent(containerSize)
    }
}

// ══════════════════════════════════
// DRAGGABLE BOX (Blur / Logo)
// ══════════════════════════════════

@Composable
fun DraggableBox(
    containerSize: IntSize,
    initialX: Float = 0.15f,  // fraction of container
    initialY: Float = 0.15f,
    initialW: Float = 0.3f,
    initialH: Float = 0.2f,
    borderColor: Color = Rose,
    label: String = "Blur",
    onRemove: (() -> Unit)? = null,
    onCoordsChanged: (BlurArea) -> Unit,
) {
    val density = LocalDensity.current
    var offsetX by remember { mutableFloatStateOf(initialX * containerSize.width) }
    var offsetY by remember { mutableFloatStateOf(initialY * containerSize.height) }
    var boxW by remember { mutableFloatStateOf(initialW * containerSize.width) }
    var boxH by remember { mutableFloatStateOf(initialH * containerSize.height) }

    // Update when container size changes
    LaunchedEffect(containerSize) {
        if (containerSize.width > 0) {
            offsetX = initialX * containerSize.width
            offsetY = initialY * containerSize.height
            boxW = initialW * containerSize.width
            boxH = initialH * containerSize.height
        }
    }

    fun emitCoords() {
        if (containerSize.width > 0) {
            onCoordsChanged(BlurArea(
                x = offsetX.roundToInt(), y = offsetY.roundToInt(),
                w = boxW.roundToInt(), h = boxH.roundToInt(),
            ))
        }
    }

    if (containerSize.width == 0) return

    Box(
        Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(with(density) { boxW.toDp() }, with(density) { boxH.toDp() })
            .border(2.dp, borderColor.copy(0.6f), RoundedCornerShape(4.dp))
            .background(borderColor.copy(0.15f), RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX = (offsetX + dragAmount.x).coerceIn(0f, (containerSize.width - boxW).coerceAtLeast(0f))
                    offsetY = (offsetY + dragAmount.y).coerceIn(0f, (containerSize.height - boxH).coerceAtLeast(0f))
                    emitCoords()
                }
            }
    ) {
        // Label
        Text(label, color = borderColor, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(2.dp))

        // Remove button
        if (onRemove != null) {
            Box(
                Modifier.align(Alignment.TopEnd).size(20.dp).offset(x = 8.dp, y = (-8).dp)
                    .clip(CircleShape).background(Color.White).border(1.5.dp, borderColor, CircleShape)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = borderColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Resize handle
        Box(
            Modifier.align(Alignment.BottomEnd).size(18.dp).offset(x = 6.dp, y = 6.dp)
                .clip(CircleShape).background(Color.White).border(1.5.dp, borderColor.copy(0.5f), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        boxW = (boxW + dragAmount.x).coerceIn(40f, (containerSize.width - offsetX).toFloat())
                        boxH = (boxH + dragAmount.y).coerceIn(30f, (containerSize.height - offsetY).toFloat())
                        emitCoords()
                    }
                },
        )
    }
}

// ══════════════════════════════════
// LOADING OVERLAY
// ══════════════════════════════════

@Composable
fun LoadingOverlay(visible: Boolean, message: String = "Processing...") {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(DarkBg.copy(0.85f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Purple, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(message, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

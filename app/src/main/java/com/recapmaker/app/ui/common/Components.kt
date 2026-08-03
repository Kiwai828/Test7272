package com.recapmaker.app.ui.common

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.recapmaker.app.data.model.BlurArea
import com.recapmaker.app.data.model.VoiceGender
import com.recapmaker.app.util.formatFileSize
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
    errorText: String? = null,
) {
    val isError = errorText != null
    val accent = if (isError) ErrorRed else Purple
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        singleLine = true, isError = isError,
        modifier = modifier.fillMaxWidth().animateContentSize(),
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions { onImeAction() },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = if (isError) ErrorRed else CardBorder,
            focusedLabelColor = accent,
            unfocusedLabelColor = TextDim,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = accent,
            errorBorderColor = ErrorRed,
            errorCursorColor = ErrorRed,
            errorLabelColor = ErrorRed,
        ),
        shape = MaterialTheme.shapes.small,
    )
    if (isError && errorText != null) {
        Text(errorText, color = ErrorRed, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp, top = 2.dp))
    }
}

@Composable
fun PasswordField(
    value: String, onValueChange: (String) -> Unit, label: String = "Password",
    modifier: Modifier = Modifier, imeAction: ImeAction = ImeAction.Done, onImeAction: () -> Unit = {},
    errorText: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    val isError = errorText != null
    val accent = if (isError) ErrorRed else Purple
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        singleLine = true, isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = if (isError) ErrorRed else TextDim)
            }
        },
        modifier = modifier.fillMaxWidth().animateContentSize(),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions { onImeAction() },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = if (isError) ErrorRed else CardBorder,
            focusedLabelColor = accent,
            unfocusedLabelColor = TextDim,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = accent,
            errorBorderColor = ErrorRed,
            errorCursorColor = ErrorRed,
            errorLabelColor = ErrorRed,
        ),
        shape = MaterialTheme.shapes.small,
    )
    if (isError && errorText != null) {
        Text(errorText, color = ErrorRed, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp, top = 2.dp))
    }
}

// ══════════════════════════════════
// BUTTONS — filled, outlined, text
// ══════════════════════════════════

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, loading: Boolean = false, modifier: Modifier = Modifier, color: Color = Purple, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .animateContentSize(animationSpec = tween(300)),
        enabled = !loading && enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = OnPrimary,
            disabledContainerColor = color.copy(0.4f),
            disabledContentColor = OnPrimary,
        ),
    ) {
        Crossfade(targetKey = loading) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = OnPrimary, strokeWidth = 2.dp)
            } else {
                Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun AppOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Purple,
    contentColor: Color = TextPrimary,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .animateContentSize(animationSpec = tween(300)),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (enabled) color else CardBorder.copy(0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (enabled) contentColor else TextDim,
            disabledContainerColor = Color.Transparent,
        ),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Purple,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.animateContentSize(animationSpec = tween(300)),
        enabled = enabled,
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) color else TextDim)
    }
}

// ══════════════════════════════════
// ICON BUTTON — consistent sizing & padding
// ══════════════════════════════════

@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDesc: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextDim,
    size: Dp = 18.dp,
) {
    val hit = if (size < 20.dp) 36.dp else 40.dp
    Box(
        modifier = modifier
            .size(hit)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDesc, tint = tint, modifier = Modifier.size(size))
    }
}

// ══════════════════════════════════
// ERROR BANNER
// ══════════════════════════════════

@Composable
fun ErrorBanner(message: String?) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically { -it },
        exit = fadeOut(animationSpec = tween(200)),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                message ?: "",
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 13.sp,
                modifier = Modifier.padding(12.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
    if (message != null) Spacer(Modifier.height(12.dp))
}

// ══════════════════════════════════
// COIN BADGE
// ══════════════════════════════════

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
    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        color = CardBg,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, CardBorder),
        tonalElevation = 2.dp,
    ) {
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
                checkedTrackColor = switchColor, checkedThumbColor = OnPrimary, uncheckedTrackColor = SurfaceDark, uncheckedBorderColor = CardBorder))
        }
    }
}

// ══════════════════════════════════
// VOICE CARD & TAB
// ══════════════════════════════════

@Composable
fun VoiceCard(name: String, label: String, gender: VoiceGender, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val gc = when (gender) { VoiceGender.Male -> Cyan; VoiceGender.Female -> Rose; VoiceGender.Neutral -> Purple }
    Surface(modifier = modifier
        .clickable { onClick() }
        .animateItemPlacement(animationSpec = tween(300)),
        color = if (isSelected) Purple.copy(0.08f) else CardBg.copy(0.65f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isSelected) Purple.copy(0.4f) else Purple.copy(0.07f))) {
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
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (sel) OnPrimary else TextMid)
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
    val bg = if (selected) Purple.copy(0.15f) else Color.Transparent
    val borderClr = if (selected) Purple.copy(0.4f) else CardBorder
    val txtClr = if (selected) Purple else TextDim
    Surface(modifier = modifier.clickable { onClick() }, color = bg,
        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, borderClr)) {
        Text(label, modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth(), textAlign = TextAlign.Center,
            fontSize = 14.sp, color = txtClr, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
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
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(videoRatio, matchHeightConstraintsFirst = false)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black)
            .onSizeChanged { containerSize = it },
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
        Text(label, color = borderColor, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(2.dp))

        if (onRemove != null) {
            Box(
                Modifier.align(Alignment.TopEnd).size(20.dp).offset(x = 8.dp, y = (-8).dp)
                    .clip(CircleShape).background(OnPrimary).border(1.5.dp, borderColor, CircleShape)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = borderColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Box(
            Modifier.align(Alignment.BottomEnd).size(18.dp).offset(x = 6.dp, y = 6.dp)
                .clip(CircleShape).background(OnPrimary).border(1.5.dp, borderColor.copy(0.5f), CircleShape)
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
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) + scaleIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200)),
    ) {
        Box(Modifier.fillMaxSize().background(DarkBg.copy(0.85f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Purple, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(message, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ══════════════════════════════════
// CHIP GROUP (for selecting options)
// ══════════════════════════════════

@Composable
fun <T> OptionChipGroup(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    isPremium: (T) -> Boolean = { false },
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options.size) { i ->
            val opt = options[i]
            val sel = selected == opt
            val premium = isPremium(opt)
            Surface(
                onClick = { onSelect(opt) },
                color = if (sel) Purple.copy(0.15f) else SurfaceDark,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (sel) Purple.copy(0.4f) else CardBorder),
                modifier = Modifier.animateItemPlacement(animationSpec = tween(300)),
            ) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label(opt), fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) OnPrimary else TextPrimary)
                    if (premium) Text("Premium", fontSize = 9.sp, color = Gold)
                }
            }
        }
    }
}

// ══════════════════════════════════
// SLIDER WITH LIVE LABEL
// ══════════════════════════════════

@Composable
fun SliderWithLabel(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, label: String, color: Color = Purple) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextDim, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("${"%.0f".format(value)}%", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = CardBorder),
        )
    }
}

// ══════════════════════════════════
// CIRCULAR PROGRESS CARD
// ══════════════════════════════════

@Composable
fun ProgressCard(visible: Boolean, progress: Float, status: String, elapsedSec: Long, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically { it },
        exit = fadeOut(animationSpec = tween(200)),
    ) {
        Surface(
            modifier = modifier.fillMaxWidth().animateContentSize(),
            color = Purple.copy(0.08f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Purple.copy(0.2f)),
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(progress = { progress }, color = Purple, strokeWidth = 4.dp, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = Purple, trackColor = CardBorder)
                        Spacer(Modifier.height(4.dp))
                        Text(status, color = TextDim, fontSize = 12.sp)
                    }
                    Text("${(progress * 100).toInt()}%", color = Purple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (elapsedSec > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("Elapsed: ${elapsedSec}s", color = TextDim, fontSize = 11.sp)
                }
            }
        }
    }
}

// ══════════════════════════════════
// EMPTY STATE
// ══════════════════════════════════

@Composable
fun EmptyState(icon: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = TextDim, textAlign = TextAlign.Center)
    }
}

// ══════════════════════════════════
// VIDEO INFO BAR
// ══════════════════════════════════

@Composable
fun VideoInfoBar(durationSec: Int, resolution: String, fileSize: Long, modifier: Modifier = Modifier) {
    val mins = durationSec / 60
    val secs = durationSec % 60
    Surface(color = SurfaceDark, shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, CardBorder)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column { Text("Duration", color = TextDim, fontSize = 10.sp); Text("${mins}:${"%02d".format(secs)}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            Column { Text("Resolution", color = TextDim, fontSize = 10.sp); Text(resolution, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            Column { Text("Size", color = TextDim, fontSize = 10.sp); Text(formatFileSize(fileSize), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ══════════════════════════════════
// ACTION BUTTON (FAB style)
// ══════════════════════════════════

@Composable
fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Emerald, enabled: Boolean = true) {
    FloatingActionButton(onClick = onClick, containerColor = color, shape = RoundedCornerShape(16.dp), modifier = modifier, enabled = enabled) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = OnPrimary, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 10.sp, color = OnPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

// ══════════════════════════════════
// MODERN DIALOG
// ══════════════════════════════════

@Composable
fun ModernDialog(title: String, message: String, confirmText: String = "OK", dismissText: String = "Cancel", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = CardBg, border = BorderStroke(1.dp, CardBorder), tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(message, fontSize = 14.sp, color = TextMid)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, CardBorder)) { Text(dismissText, color = TextDim, fontSize = 14.sp) }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small, colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = OnPrimary)) { Text(confirmText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

// ══════════════════════════════════
// ENHANCED CARD (elevated, tonal)
// ══════════════════════════════════

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = CardBg,
    shape: Shape = MaterialTheme.shapes.medium,
    border: BorderStroke = BorderStroke(1.dp, CardBorder),
    tonalElevation: Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = shape,
        border = border,
        tonalElevation = tonalElevation,
    ) {
        Column(Modifier.padding(16.dp)) {
            content()
        }
    }
}

// ══════════════════════════════════
// PROGRESS INDICATORS — themed, consistent
// ══════════════════════════════════

@Composable
fun AppCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Primary,
    strokeWidth: Dp = 2.dp,
) {
    CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
}

@Composable
fun AppLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Primary,
    trackColor: Color = CardBorder,
) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

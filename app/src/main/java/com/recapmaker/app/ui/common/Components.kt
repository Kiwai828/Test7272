package com.recapmaker.app.ui.common

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recapmaker.app.data.model.VoiceGender

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
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Purple, unfocusedBorderColor = CardBorder,
            focusedLabelColor = Purple, cursorColor = Purple,
        ),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun PasswordField(
    value: String, onValueChange: (String) -> Unit, label: String = "Password",
    modifier: Modifier = Modifier, imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        singleLine = true, modifier = modifier.fillMaxWidth(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TextDim)
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions { onImeAction() },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Purple, unfocusedBorderColor = CardBorder,
            focusedLabelColor = Purple, cursorColor = Purple,
        ),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, loading: Boolean = false, modifier: Modifier = Modifier, color: Color = Purple, enabled: Boolean = true) {
    Button(
        onClick = onClick, modifier = modifier.fillMaxWidth().height(50.dp),
        enabled = !loading && enabled, colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = color.copy(0.4f)),
        shape = RoundedCornerShape(12.dp),
    ) {
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
                Text("🥇", fontSize = 12.sp); Spacer(Modifier.width(3.dp))
                Text("$gold", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Surface(color = SilverColor.copy(0.1f), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, SilverColor.copy(0.25f))) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🥈", fontSize = 12.sp); Spacer(Modifier.width(3.dp))
                Text("$silver", color = SilverColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// ── Section Card ──
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

// ── Effect Toggle Row ──
@Composable
fun EffectToggle(label: String, icon: ImageVector, checked: Boolean, onToggle: (Boolean) -> Unit, switchColor: Color = Purple) {
    Surface(
        color = if (checked) switchColor.copy(0.06f) else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (checked) switchColor.copy(0.2f) else Purple.copy(0.07f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (checked) switchColor else TextDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onToggle, colors = SwitchDefaults.colors(
                checkedTrackColor = switchColor, checkedThumbColor = Color.White, uncheckedTrackColor = SurfaceDark, uncheckedBorderColor = CardBorder,
            ))
        }
    }
}

// ── Voice Card (2-column grid item) ──
@Composable
fun VoiceCard(name: String, label: String, gender: VoiceGender, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val genderColor = when (gender) { VoiceGender.Male -> Cyan; VoiceGender.Female -> Rose; VoiceGender.Neutral -> Purple }
    Surface(
        modifier = modifier.clickable { onClick() },
        color = if (isSelected) Purple.copy(0.08f) else CardBg.copy(0.65f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isSelected) Purple.copy(0.4f) else Purple.copy(0.07f)),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(genderColor.copy(0.1f)).border(1.dp, genderColor.copy(0.18f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center) {
                Text(label.take(2).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = genderColor)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(gender.name, fontSize = 10.sp, color = genderColor.copy(0.8f))
            }
            if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = Purple, modifier = Modifier.size(16.dp))
        }
    }
}

// ── Tab Row ──
@Composable
fun VoiceTabRow(tabs: List<Pair<String, String>>, selectedTab: String, onTabSelected: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEach { (id, label) ->
            val sel = selectedTab == id
            Surface(
                modifier = Modifier.weight(1f).clickable { onTabSelected(id) },
                color = if (sel) Purple.copy(0.12f) else SurfaceDark.copy(0.65f),
                shape = RoundedCornerShape(11.dp),
                border = BorderStroke(1.dp, if (sel) Purple.copy(0.35f) else Purple.copy(0.12f)),
            ) {
                Text(label, modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(), textAlign = TextAlign.Center,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.White else TextMid)
            }
        }
    }
}

// ── Position Selector ──
@Composable
fun PositionSelector(selected: String, onSelect: (String) -> Unit) {
    val positions = listOf(
        "top_left" to "↖ ဘယ်", "top_center" to "↑ အလယ်", "top_right" to "↗ ညာ",
        "bottom_left" to "↙ ဘယ်", "bottom_center" to "↓ အလယ်", "bottom_right" to "↘ ညာ",
        "center" to "◎ အလယ်တည့်",
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { positions.take(3).forEach { (v, l) -> PosChip(l, selected == v, Modifier.weight(1f)) { onSelect(v) } } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { PosChip(positions[6].second, selected == "center", Modifier.fillMaxWidth(0.4f)) { onSelect("center") } }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { positions.subList(3, 6).forEach { (v, l) -> PosChip(l, selected == v, Modifier.weight(1f)) { onSelect(v) } } }
    }
}

@Composable
private fun PosChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable { onClick() }, color = if (selected) Purple.copy(0.15f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, if (selected) Purple.copy(0.4f) else CardBorder)) {
        Text(label, modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp).fillMaxWidth(), textAlign = TextAlign.Center,
            fontSize = 11.sp, color = if (selected) Purple else TextDim, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

// ── Loading Overlay ──
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

package com.recapmaker.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
fun PrimaryButton(text: String, onClick: () -> Unit, loading: Boolean = false, modifier: Modifier = Modifier, color: Color = Purple) {
    Button(
        onClick = onClick, modifier = modifier.fillMaxWidth().height(50.dp),
        enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = color),
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("●", color = Gold, fontSize = 10.sp)
            Text("$gold", color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("●", color = SilverColor, fontSize = 10.sp)
            Text("$silver", color = SilverColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

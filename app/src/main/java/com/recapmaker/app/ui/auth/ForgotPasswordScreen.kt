package com.recapmaker.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recapmaker.app.ui.common.*

@Composable
fun ForgotPasswordScreen(vm: AuthViewModel, onBack: () -> Unit) {
    val s = vm.state
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    val fm = LocalFocusManager.current

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(Modifier.fillMaxSize().background(DarkBg)) {
        AnimatedVisibility(visible, enter = fadeIn(animationSpec = tween(400)) + slideInVertically(animationSpec = tween(400), initialOffset = { -it / 4 }), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary, modifier = Modifier.clickable { vm.resetState(); onBack() }.padding(8.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(if (s.resetCodeSent) "Code ဖြည့်ပါ" else "Password ပြန်ယူရန်", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(if (s.resetCodeSent) "Email ထဲ ပို့ထားတဲ့ 6-digit code" else "မှတ်ပုံတင်ထားတဲ့ Email ဖြည့်ပါ", fontSize = 13.sp, color = TextDim, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))

            if (!s.resetCodeSent) {
                AppTextField(email, { email = it }, "Email", imeAction = ImeAction.Done, onImeAction = { fm.clearFocus(); vm.forgotPassword(email.trim()) })
                Spacer(Modifier.height(16.dp))
                ErrorBanner(s.error)
                PrimaryButton("Send Code", { fm.clearFocus(); vm.forgotPassword(email.trim()) }, s.isLoading)
            } else {
                AppTextField(code, { if (it.length <= 6) code = it.filter { c -> c.isDigit() } }, "6-digit Code",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                Spacer(Modifier.height(12.dp))
                PasswordField(newPw, { newPw = it }, "New Password", imeAction = ImeAction.Next)
                Spacer(Modifier.height(12.dp))
                PasswordField(confirmPw, { confirmPw = it }, "Confirm Password", onImeAction = { fm.clearFocus() })
                Spacer(Modifier.height(16.dp))
                ErrorBanner(s.error)
                PrimaryButton("Reset Password", { fm.clearFocus(); vm.resetPassword(email.trim(), code.trim(), newPw, confirmPw) }, s.isLoading, color = Emerald)
            }
        }
        }
    }
}

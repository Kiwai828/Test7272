package com.recapmaker.app.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recapmaker.app.ui.common.*

@Composable
fun RegisterScreen(vm: AuthViewModel, onLogin: () -> Unit, onSuccess: () -> Unit) {
    val s = vm.state
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    val fm = LocalFocusManager.current
    LaunchedEffect(s.loginSuccess) { if (s.loginSuccess) onSuccess() }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(Modifier.fillMaxSize().background(DarkBg), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible, enter = fadeIn(animationSpec = tween(500)) + slideInVertically(animationSpec = tween(500), initialOffsetY = { it / 3 }), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text("🎬", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text("Create Account", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(24.dp))
            AppTextField(username, { username = it }, "Username")
            Spacer(Modifier.height(12.dp))
            AppTextField(email, { email = it }, "Email (Forgot Password အတွက်)")
            Spacer(Modifier.height(12.dp))
            PasswordField(password, { password = it }, "Password", imeAction = androidx.compose.ui.text.input.ImeAction.Next)
            Spacer(Modifier.height(12.dp))
            PasswordField(confirmPw, { confirmPw = it }, "Confirm Password", onImeAction = { fm.clearFocus() })
            Spacer(Modifier.height(16.dp))
            ErrorBanner(s.error)
            PrimaryButton("Register", { fm.clearFocus(); vm.register(username.trim(), password, confirmPw, email.trim()) }, s.isLoading, color = Emerald)
            Spacer(Modifier.height(20.dp))
            Row {
                Text("Account ရှိပြီးသားလား? ", color = TextDim, fontSize = 14.sp)
                Text("Login", color = Purple, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onLogin() })
            }
            Spacer(Modifier.height(32.dp))
        }
        }
    }
}

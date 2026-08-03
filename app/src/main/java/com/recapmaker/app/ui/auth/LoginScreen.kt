package com.recapmaker.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recapmaker.app.ui.common.*

@Composable
fun LoginScreen(vm: AuthViewModel, onRegister: () -> Unit, onForgot: () -> Unit, onSuccess: () -> Unit) {
    val s = vm.state
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val fm = LocalFocusManager.current
    LaunchedEffect(s.loginSuccess) { if (s.loginSuccess) onSuccess() }

    Box(Modifier.fillMaxSize().background(DarkBg), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Animated logo + title entrance
            val fadeIn = tween(500)
            val slideDelay = 100
            AnimatedVisibility(visible = true, enter = androidx.compose.animation.fadeIn(fadeIn)) {
                Text("🎬", fontSize = 48.sp)
            }
            Spacer(Modifier.height(8.dp))
            AnimatedVisibility(visible = true, enter = androidx.compose.animation.fadeIn(fadeIn) + androidx.compose.animation.slideInVertically(initialOffsetY = { -30 }) { slideDelay }) {
                Text("Recap Maker", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            AnimatedVisibility(visible = true, enter = androidx.compose.animation.fadeIn(fadeIn) + androidx.compose.animation.slideInVertically(initialOffsetY = { -20 }) { slideDelay + 100 }) {
                Text("Video Editor & Subtitle Generator", fontSize = 13.sp, color = TextDim)
            }
            Spacer(Modifier.height(32.dp))
            AppTextField(username, { username = it }, "Username or Email")
            Spacer(Modifier.height(12.dp))
            PasswordField(password, { password = it }, onImeAction = { fm.clearFocus(); vm.login(username.trim(), password) })
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text("Forgot Password?", color = Purple, fontSize = 13.sp, modifier = Modifier.clickable { onForgot() }.padding(vertical = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            ErrorBanner(s.error)
            PrimaryButton("Login", { fm.clearFocus(); vm.login(username.trim(), password) }, s.isLoading)
            Spacer(Modifier.height(20.dp))
            Row {
                Text("Account မရှိသေးဘူးလား? ", color = TextDim, fontSize = 14.sp)
                Text("Register", color = Purple, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onRegister() })
            }
        }
    }
}


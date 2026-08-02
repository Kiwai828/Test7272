package com.recapmaker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.recapmaker.app.data.local.TokenManager
import com.recapmaker.app.ui.auth.*
import com.recapmaker.app.ui.common.Purple
import com.recapmaker.app.ui.common.RecapTheme
import com.recapmaker.app.ui.common.TextDim
import com.recapmaker.app.ui.dashboard.*
import com.recapmaker.app.ui.editor.EditorScreen
import com.recapmaker.app.ui.editor.EditorViewModel
import com.recapmaker.app.ui.settings.SettingsScreen
import com.recapmaker.app.ui.subtitle.SubtitleScreen
import com.recapmaker.app.ui.subtitle.SubtitleViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.min

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RecapTheme {
                MainNavHost(tokenManager = tokenManager)
            }
        }
    }
}

@Composable
fun MainNavHost(tokenManager: TokenManager) {
    val nav = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination = if (tokenManager.getCachedToken().isNullOrEmpty()) "login" else "dashboard"
    }

    // Splash while determining auth state
    if (startDestination == null) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("🎬", fontSize = 48.sp)
            Text(
                "Recap Maker",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Purple,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        return
    }

    val fadeIn = tween(300)
    val fadeOut = tween(200)

    NavHost(nav, startDestination = startDestination!!) {

        composable("login", enterTransition = { fadeIn }, exitTransition = { fadeOut }, popEnterTransition = { fadeIn }) {
            val vm: AuthViewModel = hiltViewModel()
            LoginScreen(vm,
                onRegister = { vm.resetState(); nav.navigate("register") },
                onForgot = { vm.resetState(); nav.navigate("forgot_password") },
                onSuccess = { nav.navigate("dashboard") { popUpTo("login") { inclusive = true } } },
            )
        }

        composable("register", enterTransition = { fadeIn }, exitTransition = { fadeOut }) {
            val vm: AuthViewModel = hiltViewModel()
            RegisterScreen(vm,
                onLogin = { vm.resetState(); nav.popBackStack() },
                onSuccess = { nav.navigate("dashboard") { popUpTo(0) { inclusive = true } } },
            )
        }

        composable("forgot_password", enterTransition = { fadeIn }, exitTransition = { fadeOut }) {
            val vm: AuthViewModel = hiltViewModel()
            ForgotPasswordScreen(vm, onBack = { nav.popBackStack() })
        }

        composable("dashboard", enterTransition = { fadeIn }, exitTransition = { fadeOut }) {
            val vm: DashboardViewModel = hiltViewModel()
            DashboardScreen(vm,
                onEditor = { nav.navigate("editor") },
                onSubtitle = { nav.navigate("subtitle") },
                onSettings = { nav.navigate("settings") },
                onLogout = {
                    lifecycleScope.launch { tokenManager.clear() }
                    nav.navigate("login") { popUpTo(0) { inclusive = true } }
                },
            )
        }

        composable("editor", enterTransition = { fadeIn }, exitTransition = { fadeOut }) {
            val vm: EditorViewModel = hiltViewModel()
            EditorScreen(onBack = { nav.popBackStack() }, vm = vm)
        }

        composable("subtitle", enterTransition = { fadeIn }, exitTransition = { fadeOut }) {
            val vm: SubtitleViewModel = hiltViewModel()
            SubtitleScreen(onBack = { nav.popBackStack() }, vm = vm)
        }

        composable("settings", enterTransition = { fadeIn }, exitTransition = { fadeOut }) {
            val dashVm: DashboardViewModel = hiltViewModel()
            val authVm: AuthViewModel = hiltViewModel()
            SettingsScreen(
                username = dashVm.state.username,
                email = dashVm.state.email,
                vm = authVm,
                onBack = { nav.popBackStack() },
                onLogout = {
                    lifecycleScope.launch { tokenManager.clear() }
                    nav.navigate("login") { popUpTo(0) { inclusive = true } }
                },
            )
        }
    }
}

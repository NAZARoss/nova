package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.AdminChatDetailScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AdminLoginScreen
import com.example.ui.screens.AdminSettingsScreen
import com.example.ui.screens.UserChatScreen
import com.example.ui.screens.UserSettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AdminViewModel
import com.example.viewmodel.UserChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val userChatViewModel: UserChatViewModel = viewModel()
            val adminViewModel: AdminViewModel = viewModel()
            val uiState by userChatViewModel.uiState.collectAsStateWithLifecycle()

            val isDark = when (uiState.selectedTheme) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NovaAppNavigation(
                        userChatViewModel = userChatViewModel,
                        adminViewModel = adminViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun NovaAppNavigation(
    userChatViewModel: UserChatViewModel,
    adminViewModel: AdminViewModel
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "user_chat"
    ) {
        // User AI Chat Screen
        composable("user_chat") {
            UserChatScreen(
                viewModel = userChatViewModel,
                onNavigateToSettings = {
                    navController.navigate("user_settings")
                }
            )
        }

        // User Settings Screen (with 5-tap Admin trigger)
        composable("user_settings") {
            UserSettingsScreen(
                viewModel = userChatViewModel,
                onBackClick = { navController.popBackStack() },
                onSecretAdminTrigger = {
                    navController.navigate("admin_login")
                }
            )
        }

        // Hidden Admin Login Screen
        composable("admin_login") {
            AdminLoginScreen(
                adminViewModel = adminViewModel,
                onLoginSuccess = {
                    navController.navigate("admin_dashboard") {
                        popUpTo("admin_login") { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        // Admin Multi-User Dashboard
        composable("admin_dashboard") {
            AdminDashboardScreen(
                adminViewModel = adminViewModel,
                onOpenChatDetail = { userId ->
                    navController.navigate("admin_chat_detail/$userId")
                },
                onOpenSettings = {
                    navController.navigate("admin_settings")
                },
                onLogout = {
                    navController.navigate("user_chat") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Admin Conversation Detail
        composable(
            route = "admin_chat_detail/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            AdminChatDetailScreen(
                userId = userId,
                adminViewModel = adminViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Admin Node Configuration
        composable("admin_settings") {
            AdminSettingsScreen(
                adminViewModel = adminViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

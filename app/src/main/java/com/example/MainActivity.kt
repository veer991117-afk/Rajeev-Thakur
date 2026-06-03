package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.LandingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Check system dark theme mode to default
            val systemDark = isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(systemDark) }

            MyApplicationTheme(darkTheme = isDarkMode) {
                val viewModel: ChatViewModel = viewModel()
                val profile by viewModel.userProfile.collectAsState()

                var currentScreen by remember { mutableStateOf("landing") }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Crossfade(
                        targetState = currentScreen,
                        label = "ScreenCrossfade"
                    ) { screen ->
                        when (screen) {
                            "landing" -> {
                                LandingScreen(
                                    userProfile = profile,
                                    onEnterChat = { currentScreen = "chat" }
                                )
                            }
                            "chat" -> {
                                BackHandler {
                                    currentScreen = "landing"
                                }
                                ChatScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = "landing" },
                                    onNavigateProfile = { currentScreen = "profile" },
                                    onNavigateSettings = { currentScreen = "settings" }
                                )
                            }
                            "profile" -> {
                                BackHandler {
                                    currentScreen = "chat"
                                }
                                ProfileScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = "chat" }
                                )
                            }
                            "settings" -> {
                                BackHandler {
                                    currentScreen = "chat"
                                }
                                SettingsScreen(
                                    viewModel = viewModel,
                                    isDarkMode = isDarkMode,
                                    onToggleDarkMode = { isDarkMode = it },
                                    onNavigateBack = { currentScreen = "chat" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

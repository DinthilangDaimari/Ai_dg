package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.preferences.ThemeMode
import com.example.ui.navigation.Screen
import com.example.ui.screens.CallDetailScreen
import com.example.ui.screens.CallSimulatorScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LiveScreeningScreen
import com.example.ui.screens.OutboundCallScreen
import com.example.ui.screens.ScheduleCallScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TelephonyProviderScreen
import com.example.ui.screens.VoiceNotesScreen
import com.example.ui.theme.DinthiTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.preferences.themeMode.collectAsStateWithLifecycle()
            val isDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            DinthiTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DinthiAppNavigation(
                        viewModel = viewModel,
                        hasAudioPermission = {
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DinthiAppNavigation(
    viewModel: MainViewModel,
    hasAudioPermission: () -> Boolean
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingMicAction?.invoke()
        }
        pendingMicAction = null
    }

    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled gracefully
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        // Small delay to let the initial SurfaceSyncGroup window render complete cleanly before showing system dialogs
        kotlinx.coroutines.delay(400)
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_CONTACTS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissionsToRequest.isNotEmpty()) {
            multiPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    val requestMicPermission: (() -> Unit) -> Unit = { onGranted ->
        if (hasAudioPermission()) {
            onGranted()
        } else {
            pendingMicAction = onGranted
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val showBottomBar = currentScreen is Screen.Dashboard || currentScreen is Screen.Profile

    androidx.compose.material3.Scaffold(
        bottomBar = {
            if (showBottomBar) {
                com.example.ui.components.DinthiBottomBar(
                    currentScreen = currentScreen,
                    onNavigate = { dest -> viewModel.navigateTo(dest) }
                )
            }
        }
    ) { paddingValues ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val screen = currentScreen) {
                is Screen.Dashboard -> {
                    DashboardScreen(viewModel = viewModel)
                }
                is Screen.Profile -> {
                    com.example.ui.screens.ProfileScreen(viewModel = viewModel)
                }
                is Screen.Instructions -> {
                    com.example.ui.screens.InstructionsScreen(viewModel = viewModel)
                }
                is Screen.HealthCheck -> {
                    com.example.ui.screens.HealthCheckScreen(viewModel = viewModel)
                }
                is Screen.Addresses -> {
                    com.example.ui.screens.AddressesScreen(viewModel = viewModel)
                }
                is Screen.CallSomeone -> {
                    com.example.ui.screens.CallSomeoneScreen(viewModel = viewModel)
                }
                is Screen.OutboundCall -> {
                    OutboundCallScreen(
                        sessionId = screen.sessionId,
                        viewModel = viewModel
                    )
                }
                is Screen.TelephonyProviderSetup -> {
                    TelephonyProviderScreen(
                        viewModel = viewModel
                    )
                }
                is Screen.LiveScreening -> {
                    LiveScreeningScreen(
                        callId = screen.callId,
                        viewModel = viewModel
                    )
                }
                is Screen.CallDetail -> {
                    CallDetailScreen(
                        callId = screen.callId,
                        viewModel = viewModel
                    )
                }
                is Screen.CallSimulator -> {
                    CallSimulatorScreen(
                        viewModel = viewModel
                    )
                }
                is Screen.ScheduleCall -> {
                    ScheduleCallScreen(
                        viewModel = viewModel
                    )
                }
                is Screen.VoiceNotes -> {
                    VoiceNotesScreen(
                        viewModel = viewModel
                    )
                }
                is Screen.Settings -> {
                    SettingsScreen(
                        viewModel = viewModel
                    )
                }
                is Screen.Chat -> {
                    ChatScreen(
                        conversationId = screen.conversationId,
                        viewModel = viewModel,
                        onRequestMicrophonePermission = requestMicPermission
                    )
                }
            }
        }
    }
}

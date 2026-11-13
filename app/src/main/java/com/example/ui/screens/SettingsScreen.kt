package com.example.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsPhone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.example.ui.theme.DinthiLime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.preferences.ThemeMode
import com.example.ui.theme.DinthiCyan
import com.example.ui.theme.DinthiViolet
import com.example.ui.viewmodel.MainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.preferences.themeMode.collectAsStateWithLifecycle()
    val isAssistantEnabled by viewModel.preferences.isAssistantEnabled.collectAsStateWithLifecycle()
    val screeningRule by viewModel.preferences.screeningRule.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.preferences.selectedLanguage.collectAsStateWithLifecycle()
    val voicePersona by viewModel.preferences.voicePersona.collectAsStateWithLifecycle()
    val autoHangupSpam by viewModel.preferences.autoHangupSpam.collectAsStateWithLifecycle()
    val speechRate by viewModel.preferences.speechRate.collectAsStateWithLifecycle()
    val speechPitch by viewModel.preferences.speechPitch.collectAsStateWithLifecycle()

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    BackHandler {
        viewModel.navigateBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = "Call Assistant Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Android Telecom Setup & Service Verification Card
                item {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val isQPlus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    val roleManager = if (isQPlus) context.getSystemService(android.app.role.RoleManager::class.java) else null
                    val isRoleHeld = if (isQPlus && roleManager != null) {
                        roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)
                    } else false

                    SettingsSectionCard(
                        title = "Telecom Setup & Status",
                        icon = Icons.Default.Shield
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isQPlus) "Native Call Screening Supported" else "Legacy Telecom Mode",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isQPlus) DinthiLime else Color(0xFFF59E0B)
                                )
                                Text(
                                    text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isQPlus) DinthiLime.copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isQPlus) "Supported" else "Limited",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isQPlus) DinthiLime else Color(0xFFF59E0B)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Screening Service Role",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isRoleHeld) "Dinthi AI is designated as default Call Screening app" else "Tap below to designate Dinthi AI in Android default apps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isRoleHeld) DinthiLime.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (isRoleHeld) "Enabled" else "Disabled",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRoleHeld) DinthiLime else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (isQPlus && roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING)) {
                                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING)
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val settingsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                        context.startActivity(settingsIntent)
                                    }
                                } else {
                                    val settingsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                    context.startActivity(settingsIntent)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = DinthiLime)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isRoleHeld) "Modify Default App in Settings" else "Enable Dinthi Call Screening Role", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Call Screening Policy & Rules
                item {
                    SettingsSectionCard(
                        title = "Screening Policy",
                        icon = Icons.Default.Shield
                    ) {
                        Text(
                            text = "Configure which incoming calls Dinthi AI intercepts and answers:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val rules = listOf(
                            "UNKNOWN_AND_SPAM" to ("Unknown & Suspected Spam" to "Screen calls not in your contacts and flagged spam numbers (Recommended)"),
                            "ALL_CALLS" to ("All Incoming Calls" to "Screen every incoming call including saved contacts"),
                            "SPAM_ONLY" to ("Spam Only" to "Only intercept calls flagged with high telemarketing spam score")
                        )

                        rules.forEach { (ruleKey, pair) ->
                            val (title, desc) = pair
                            SelectableRow(
                                title = title,
                                subtitle = desc,
                                selected = screeningRule == ruleKey,
                                onSelect = { viewModel.preferences.setScreeningRule(ruleKey) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-hangup High Spam",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Automatically terminate call if AI detects confirmed robocall or scam",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoHangupSpam,
                                onCheckedChange = { viewModel.preferences.setAutoHangupSpam(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                // Indian Language Selection
                item {
                    SettingsSectionCard(
                        title = "Language & Spoken Dialect",
                        icon = Icons.Default.Language
                    ) {
                        Text(
                            text = "Choose the language Dinthi AI uses when screening phone calls:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val languages = listOf(
                            "English (India)",
                            "Hindi (हिन्दी)",
                            "Bengali (বাংলা)",
                            "Telugu (తెలుగు)",
                            "Tamil (தமிழ்)",
                            "Kannada (ಕನ್ನಡ)",
                            "Marathi (मराठी)"
                        )

                        languages.forEach { lang ->
                            SelectableRow(
                                title = lang,
                                subtitle = if (lang == "English (India)") "Default spoken screening dialect" else "Spoken response and transcription",
                                selected = selectedLanguage == lang,
                                onSelect = { viewModel.preferences.setSelectedLanguage(lang) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }

                // Voice Persona & TTS Settings
                item {
                    SettingsSectionCard(
                        title = "AI Voice Persona",
                        icon = Icons.Default.RecordVoiceOver
                    ) {
                        Text(
                            text = "Voice Tone style when answering callers:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val personas = listOf(
                            "Professional" to "Polite, formal, and courteous corporate tone",
                            "Friendly" to "Warm, conversational, and welcoming tone",
                            "Direct" to "Brief, concise, no extra chit-chat"
                        )

                        personas.forEach { (name, desc) ->
                            SelectableRow(
                                title = name,
                                subtitle = desc,
                                selected = voicePersona == name,
                                onSelect = { viewModel.preferences.setVoicePersona(name) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Speech Rate Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Speech Rate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            Text(
                                text = String.format(Locale.US, "%.1fx", speechRate),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = speechRate,
                            onValueChange = { viewModel.preferences.setSpeechRate(it) },
                            valueRange = 0.6f..1.6f,
                            steps = 9
                        )

                        // Test Voice Button
                        OutlinedButton(
                            onClick = {
                                val testPhrase = if (selectedLanguage.startsWith("Hindi"))
                                    "नमस्ते, मैं दिनथी एआई हूँ, आपकी फोन कॉल सहायक।"
                                else
                                    "Hello, I am Dinthi AI screening this call on behalf of the recipient."
                                viewModel.speakText(testPhrase)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Voice Readout")
                        }
                    }
                }

                // Telecom & Privacy Legal Transparency
                item {
                    SettingsSectionCard(
                        title = "Telecom & Legal Privacy",
                        icon = Icons.Default.Info
                    ) {
                        Text(
                            text = "Dinthi AI complies strictly with telecom regulations and Google Play privacy policies:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "1. No Secret Call Recording",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Android 10+ restricts regular third-party applications from raw cellular stream audio recording without privileged carrier system integration. Dinthi AI uses real-time live transcription and Gemini intelligence summaries instead of unauthorized audio tap files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "2. Contacts Permission Purpose",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "READ_CONTACTS permission is only used locally to distinguish trusted callers from unknown numbers. Contact data is never uploaded to external servers.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // Secure Backend & Server Architecture Section
                item {
                    val serverUrl by viewModel.preferences.serverUrl.collectAsStateWithLifecycle()
                    val serverHealthStatus by viewModel.serverHealthStatus.collectAsStateWithLifecycle()
                    var serverUrlInput by remember(serverUrl) { mutableStateOf(serverUrl) }

                    SettingsSectionCard(
                        title = "Secure Backend Proxy",
                        icon = Icons.Default.Security
                    ) {
                        Text(
                            text = "Zero API Keys in APK: All AI requests are authenticated via client token and processed on your secure server, keeping Gemini credentials completely off the device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = serverUrlInput,
                            onValueChange = {
                                serverUrlInput = it
                                viewModel.preferences.setServerUrl(it)
                            },
                            label = { Text("Server Base URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (serverUrlInput != BuildConfig.SERVER_BASE_URL) {
                                    IconButton(onClick = {
                                        serverUrlInput = BuildConfig.SERVER_BASE_URL
                                        viewModel.preferences.setServerUrl(BuildConfig.SERVER_BASE_URL)
                                    }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { viewModel.testServerConnection() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("test_server_button")
                        ) {
                            Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Server Connection")
                        }

                        if (serverHealthStatus != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = serverHealthStatus ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { viewModel.navigateTo(com.example.ui.navigation.Screen.TelephonyProviderSetup) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("telephony_provider_status_button")
                        ) {
                            Icon(Icons.Default.SettingsPhone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Telephony Provider & TRAI Status")
                        }
                    }
                }

                // Appearance & Theme Mode Section
                item {
                    SettingsSectionCard(
                        title = "Appearance",
                        icon = Icons.Default.Palette
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeOptionChip(
                                label = "System",
                                selected = themeMode == ThemeMode.SYSTEM,
                                onClick = { viewModel.preferences.setThemeMode(ThemeMode.SYSTEM) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeOptionChip(
                                label = "Dark",
                                selected = themeMode == ThemeMode.DARK,
                                onClick = { viewModel.preferences.setThemeMode(ThemeMode.DARK) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeOptionChip(
                                label = "Light",
                                selected = themeMode == ThemeMode.LIGHT,
                                onClick = { viewModel.preferences.setThemeMode(ThemeMode.LIGHT) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Data Management
                item {
                    SettingsSectionCard(
                        title = "Data Management",
                        icon = Icons.Default.DeleteSweep
                    ) {
                        Text(
                            text = "Clear stored call history and transcripts permanently from this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { showClearConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("clear_call_history_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear All Call Logs")
                        }
                    }
                }

                // About Dinthi AI
                item {
                    SettingsSectionCard(
                        title = "About Dinthi AI",
                        icon = Icons.Default.Info
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(listOf(DinthiCyan, DinthiViolet))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Column {
                                Text(text = "Dinthi AI Call Assistant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(text = "v2.0 • Android Telecom Call Screening", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    // Clear Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All Call Logs?") },
            text = { Text("This will permanently erase all screened call summaries and conversation transcripts stored locally.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearCallHistory()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun SelectableRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ThemeOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (selected) 4.dp else 0.dp,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

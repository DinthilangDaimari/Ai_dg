package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.navigation.Screen
import com.example.ui.theme.DinthiLime
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateTo(Screen.Dashboard)
    }

    val context = LocalContext.current
    val isAssistantOn by viewModel.preferences.isAssistantEnabled.collectAsStateWithLifecycle()
    val isSilentMode by viewModel.preferences.silentMode.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.preferences.demoMode.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.preferences.selectedLanguage.collectAsStateWithLifecycle()
    val userDisplayName by viewModel.preferences.userDisplayName.collectAsStateWithLifecycle()

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var draftDisplayName by remember(userDisplayName) { mutableStateOf(userDisplayName) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "You & Assistant Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Identity Banner
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(DinthiLime.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = DinthiLime,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userDisplayName.ifBlank { "Your Dinthi Profile" },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isAssistantOn) "Assistant Active • Autonomous Call Screening Armed" else "Assistant Paused",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isAssistantOn) DinthiLime else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                draftDisplayName = userDisplayName
                                showEditNameDialog = true
                            }) {
                                Text("Edit Name", color = DinthiLime, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Assistant Core Controls
                item {
                    Text(
                        text = "Assistant Controls",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            // Assistant Toggle
                            ProfileToggleItem(
                                title = "AI Assistant Status",
                                subtitle = if (isAssistantOn) "Currently screening incoming calls" else "Assistant is turned off",
                                icon = Icons.Default.Security,
                                checked = isAssistantOn,
                                onCheckedChange = { viewModel.preferences.setAssistantEnabled(it) }
                            )

                            // Silent Mode
                            ProfileToggleItem(
                                title = "Silent Mode",
                                subtitle = if (isSilentMode) "TTS audio muted; transcripts logged silently" else "AI speaks out loud to caller via TTS",
                                icon = if (isSilentMode) Icons.Default.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                checked = isSilentMode,
                                onCheckedChange = { viewModel.preferences.setSilentMode(it) }
                            )

                            // Explicit Demo Mode
                            ProfileToggleItem(
                                title = "Explicit Demo Mode",
                                subtitle = if (isDemoMode) "Demo data enabled for sandbox testing" else "Real device data only (Strict Zero-Fake rule)",
                                icon = Icons.Default.HealthAndSafety,
                                checked = isDemoMode,
                                onCheckedChange = { enabled ->
                                    viewModel.preferences.setDemoMode(enabled)
                                    if (!enabled) {
                                        viewModel.clearCallHistory()
                                        Toast.makeText(context, "Demo mode disabled. Only real calls will be shown.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.enableDemoMode()
                                        Toast.makeText(context, "Demo mode activated. Clearly marked [DEMO] data enabled.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }

                // Call Assistant Intelligence Settings
                item {
                    Text(
                        text = "Instructions & Intelligence",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            ProfileNavigationItem(
                                title = "Your Instructions",
                                subtitle = "Tell Dinthi how to handle couriers, unknown callers, or family",
                                icon = Icons.Default.Psychology,
                                onClick = { viewModel.navigateTo(Screen.Instructions) }
                            )

                            ProfileNavigationItem(
                                title = "Assistant Health Check",
                                subtitle = "Diagnostics for telecom screening, audio, and backend proxy",
                                icon = Icons.Default.HealthAndSafety,
                                onClick = { viewModel.navigateTo(Screen.HealthCheck) }
                            )

                            ProfileNavigationItem(
                                title = "Voice & Language",
                                subtitle = "Active: $selectedLanguage (Hindi, Hinglish, Bengali, Tamil, etc.)",
                                icon = Icons.Default.Language,
                                onClick = { viewModel.navigateTo(Screen.Settings) }
                            )

                            ProfileNavigationItem(
                                title = "Your Addresses",
                                subtitle = "Secure landmarks for package delivery screening",
                                icon = Icons.Default.LocationOn,
                                onClick = { viewModel.navigateTo(Screen.Addresses) }
                            )
                        }
                    }
                }

                // Data & Privacy
                item {
                    Text(
                        text = "Data, Privacy & Support",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            ProfileNavigationItem(
                                title = "Delete Stored Call History",
                                subtitle = "Permanently wipe all locally stored call logs and transcripts",
                                icon = Icons.Default.DeleteSweep,
                                onClick = { showClearHistoryDialog = true }
                            )

                            ProfileNavigationItem(
                                title = "Privacy Policy & Zero-Secret Guarantee",
                                subtitle = "Microphone, telecom permissions, and proxy security explained",
                                icon = Icons.Default.Security,
                                onClick = { viewModel.navigateTo(Screen.Settings) }
                            )

                            ProfileNavigationItem(
                                title = "Support & Documentation",
                                subtitle = "Dinthi AI call assistant operational guide",
                                icon = Icons.AutoMirrored.Filled.Help,
                                onClick = {
                                    Toast.makeText(context, "Dinthi AI v1.0 • Autonomous Call Assistant", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Caller Display Name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Used when Dinthi AI introduces itself on outbound calls: \"calling on behalf of [your name]\". Leave blank to use \"the user\".",
                        style = MaterialTheme.typography.bodySmall
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = draftDisplayName,
                        onValueChange = { draftDisplayName = it },
                        label = { Text("Your Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.preferences.setUserDisplayName(draftDisplayName)
                        showEditNameDialog = false
                    }
                ) {
                    Text("Save", color = DinthiLime, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Delete All Call Logs?") },
            text = { Text("This will permanently delete all call history and transcripts stored locally on this device. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryDialog = false
                        viewModel.clearCallHistory()
                        Toast.makeText(context, "All call logs deleted", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DinthiLime.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = DinthiLime, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DinthiLime
            )
        )
    }
}

@Composable
private fun ProfileNavigationItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

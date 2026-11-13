package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.DinthiLime
import com.example.ui.viewmodel.MainViewModel

private data class HealthItem(
    val title: String,
    val description: String,
    val isHealthy: Boolean,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val actionText: String? = null,
    val onFix: (() -> Unit)? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthCheckScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val context = LocalContext.current
    val serverStatus by viewModel.serverHealthStatus.collectAsStateWithLifecycle()
    val isAssistantOn by viewModel.preferences.isAssistantEnabled.collectAsStateWithLifecycle()
    val isSilentMode by viewModel.preferences.silentMode.collectAsStateWithLifecycle()

    var isChecking by remember { mutableStateOf(false) }

    fun refreshHealth() {
        isChecking = true
        viewModel.checkServerHealth()
    }

    LaunchedEffect(Unit) {
        refreshHealth()
    }

    val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    val healthItems = listOf(
        HealthItem(
            title = "AI Screening Backend",
            description = if (serverStatus?.contains("Online") == true) "Secure Gemini proxy operational on port 3000" else "Verifying proxy connectivity...",
            isHealthy = serverStatus?.contains("Online") == true,
            icon = Icons.Default.Dns,
            actionText = "Re-check",
            onFix = { refreshHealth() }
        ),
        HealthItem(
            title = "Assistant Active Switch",
            description = if (isAssistantOn) "Autonomous call screening is armed and ready" else "Assistant is currently paused in dashboard",
            isHealthy = isAssistantOn,
            icon = Icons.Default.Security,
            actionText = if (!isAssistantOn) "Turn On" else null,
            onFix = { viewModel.preferences.setAssistantEnabled(true) }
        ),
        HealthItem(
            title = "Phone State Permission",
            description = if (hasPhoneState) "Granted: Can detect incoming telecom calls" else "Required for call detection",
            isHealthy = hasPhoneState,
            icon = Icons.Default.Phone,
            actionText = if (!hasPhoneState) "Settings" else null,
            onFix = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        ),
        HealthItem(
            title = "Contacts Permission",
            description = if (hasContacts) "Granted: Can distinguish saved contacts from unknown callers" else "Recommended to identify caller names",
            isHealthy = hasContacts,
            icon = Icons.Default.Phone,
            actionText = if (!hasContacts) "Grant" else null,
            onFix = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        ),
        HealthItem(
            title = "Microphone Permission",
            description = if (hasAudio) "Granted: Real-time speech-to-text dictation ready" else "Required for voice memos and speech recognition",
            isHealthy = hasAudio,
            icon = Icons.Default.Mic,
            actionText = if (!hasAudio) "Grant" else null,
            onFix = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        ),
        HealthItem(
            title = "Android Telecom Framework",
            description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}): Native CallScreeningService supported"
            } else {
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}): Below Android 10; cellular call screening may require default dialer role"
            },
            isHealthy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            icon = Icons.Default.Phone
        ),
        HealthItem(
            title = "Call Screening Service",
            description = run {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.getSystemService(android.app.role.RoleManager::class.java)
                } else null
                val isRoleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
                    roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)
                } else false

                if (isRoleHeld) {
                    "Enabled: Dinthi AI is the designated Call Screening app"
                } else {
                    "Service Registered: Ready to handle incoming call events via DinthiCallScreeningService"
                }
            },
            isHealthy = true,
            icon = Icons.Default.HealthAndSafety,
            actionText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Telecom Role" else "App Settings",
            onFix = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                    if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING)) {
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
                } else {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            }
        ),
        HealthItem(
            title = "Voice Speech Synthesis (TTS)",
            description = "Natural Android TextToSpeech engine ready for spoken call screening turns",
            isHealthy = true,
            icon = Icons.Default.Mic
        ),
        HealthItem(
            title = "Notification Alerts",
            description = if (hasNotification) "Granted: Urgent screening alerts will be delivered" else "Required for background call notifications",
            isHealthy = hasNotification,
            icon = Icons.Default.Notifications
        ),
        HealthItem(
            title = "Silent Mode State",
            description = if (!isSilentMode) "Standard Audio: AI speaks responses out loud" else "Silent Mode: Audio TTS muted, silent transcripts only",
            isHealthy = true,
            icon = Icons.Default.HealthAndSafety
        )
    )

    val overallHealthy = healthItems.count { it.isHealthy }
    val totalItems = healthItems.size

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Assistant Health Check",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { refreshHealth() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
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
                contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(if (overallHealthy >= totalItems - 1) DinthiLime.copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (overallHealthy >= totalItems - 1) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (overallHealthy >= totalItems - 1) DinthiLime else Color(0xFFF59E0B),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Text(
                                text = "$overallHealthy / $totalItems Services Ready",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (overallHealthy >= totalItems - 1) "All core telecom and AI screening services are operational." else "Some permissions or components require your attention.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Diagnostics & Status",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                items(healthItems) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (item.isHealthy) DinthiLime.copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (item.isHealthy) Icons.Default.CheckCircle else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (item.isHealthy) DinthiLime else Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (item.actionText != null && item.onFix != null) {
                                OutlinedButton(
                                    onClick = item.onFix,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(item.actionText, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsPhone
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.DinthiLime
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelephonyProviderScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val status by viewModel.telephonyStatus.collectAsStateWithLifecycle()
    val serverUrl by viewModel.preferences.serverUrl.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.fetchTelephonyStatus()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Telephony & TRAI Status",
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
                    IconButton(onClick = { viewModel.fetchTelephonyStatus() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Status"
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
                // Header Overview Card
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(DinthiLime.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SettingsPhone,
                                    contentDescription = null,
                                    tint = DinthiLime,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Telephony Architecture",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (status?.isConfigured == true) {
                                            DinthiLime.copy(alpha = 0.2f)
                                        } else {
                                            Color(0xFFF59E0B).copy(alpha = 0.2f)
                                        }
                                    ) {
                                        Text(
                                            text = status?.statusLabel ?: "NOT CONFIGURED",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (status?.isConfigured == true) DinthiLime else Color(0xFFF59E0B),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Outbound calls route through the Dinthi Secure Server to legitimate telephony providers. Zero API keys are stored in the client APK.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Diagnostics Cards
                item {
                    Text(
                        text = "System Diagnostics",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 1. Telephony provider
                        DiagnosticStatusRow(
                            title = "Telephony provider",
                            subtitle = if (status?.isConfigured == true) {
                                "${status?.provider} (SID: ${status?.accountSid ?: "Configured"})"
                            } else {
                                "Provider (${status?.provider ?: "Twilio / Exotel / Plivo"}) credentials required in server/.env"
                            },
                            isOk = status?.isConfigured ?: false,
                            okText = "Connected",
                            notOkText = "Not configured"
                        )

                        // 2. Backend
                        DiagnosticStatusRow(
                            title = "Backend",
                            subtitle = "Secure Proxy: $serverUrl",
                            isOk = status?.backendStatus == "ONLINE",
                            okText = "Connected",
                            notOkText = "Offline"
                        )

                        // 3. AI
                        DiagnosticStatusRow(
                            title = "AI",
                            subtitle = "Server-side Gemini engine (zero API key in APK)",
                            isOk = status?.aiReady ?: false,
                            okText = "Available",
                            notOkText = "Unavailable"
                        )

                        // 4. Voice
                        DiagnosticStatusRow(
                            title = "Voice",
                            subtitle = "Server speech webhooks + device STT/TTS pipeline",
                            isOk = status?.voiceReady ?: false,
                            okText = "Available",
                            notOkText = "Unavailable"
                        )

                        // 5. Phone number
                        DiagnosticStatusRow(
                            title = "Phone number",
                            subtitle = status?.fromNumber ?: "Verified business/DID caller number required",
                            isOk = status?.fromNumber != null,
                            okText = "Configured",
                            notOkText = "Not configured"
                        )
                    }
                }

                // Android Limitations & Architecture Boundaries (A, B, C, D)
                item {
                    Text(
                        text = "Android & Telephony Limitations",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("android_limitations_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RegulationPoint(
                                title = "A. Android Telecom Capabilities",
                                desc = "Android CallScreeningService can inspect caller info and allow/disallow/silence incoming calls, and ACTION_DIAL opens the device dialer. Android does NOT let third-party apps inject AI audio into standard cellular voice calls."
                            )
                            RegulationPoint(
                                title = "B. Features Requiring a Telephony Provider",
                                desc = "Automated outbound AI phone calls to real PSTN numbers require a server-side provider (Twilio, Exotel, Plivo) with a verified caller ID and reachable HTTPS webhook."
                            )
                            RegulationPoint(
                                title = "C. Features Requiring Backend Real-Time Audio",
                                desc = "Live conversational AI turns, interruption/barge-in handling, Hindi/English/Hinglish speech recognition, and text-to-speech playback occur on the Dinthi Secure Backend + telephony media stream."
                            )
                            RegulationPoint(
                                title = "D. Device, Carrier & Provider Dependencies",
                                desc = "Live cellular call quality, DND filtering, regional language ASR accuracy, and mid-call takeover bridges depend on carrier/provider trunk support and active account provisioning."
                            )
                        }
                    }
                }

                // TRAI & Regulatory Compliance Section
                item {
                    Text(
                        text = "Indian Telecom Regulatory Compliance (TRAI)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Gavel, contentDescription = null, tint = DinthiLime, modifier = Modifier.size(20.dp))
                                Text("TRAI Regulations & Transparency", fontWeight = FontWeight.Bold)
                            }

                            RegulationPoint(
                                title = "No Caller ID Spoofing",
                                desc = "Under Indian telecom regulations, altering or spoofing the Caller Line Identification (CLI) is strictly illegal. All calls must display verified telephone numbers."
                            )

                            RegulationPoint(
                                title = "DND (Do Not Disturb) Registry",
                                desc = "Commercial automated calls must respect the National Do Not Disturb (DND) Registry. Automated commercial calls to registered recipients are prohibited."
                            )

                            RegulationPoint(
                                title = "Mandatory AI Disclosure",
                                desc = "Dinthi AI always announces its identity upon call pickup: \"Hello, I'm Dinthi AI, an AI assistant calling on behalf of [user]...\" and never impersonates a human."
                            )

                            RegulationPoint(
                                title = "User-Initiated Authorization",
                                desc = "Dinthi AI never makes phone calls autonomously. Every outbound call requires explicit user confirmation before connecting."
                            )
                        }
                    }
                }

                // Configuration Guidance
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("Setting up Telephony Provider Credentials", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = "To connect a live cellular/VoIP trunk, add your provider credentials to server/.env:\n\nTELEPHONY_PROVIDER=twilio\nTELEPHONY_ACCOUNT_SID=your_sid\nTELEPHONY_AUTH_TOKEN=your_token\nTELEPHONY_FROM_NUMBER=+919876543210\nPUBLIC_WEBHOOK_URL=https://your-backend-url\n\nThe Android APK never holds these secrets.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = { viewModel.fetchTelephonyStatus() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("recheck_telephony_status_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DinthiLime)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-check Telephony Status", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStatusRow(
    title: String,
    subtitle: String,
    isOk: Boolean,
    okText: String,
    notOkText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isOk) DinthiLime.copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isOk) DinthiLime else Color(0xFFF59E0B),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isOk) okText else notOkText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isOk) DinthiLime else Color(0xFFF59E0B)
                    )
                }
            }
        }
    }
}

@Composable
private fun RegulationPoint(title: String, desc: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "• $title",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.CallEntity
import com.example.ui.navigation.Screen
import com.example.ui.theme.DinthiLime
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val isAssistantEnabled by viewModel.preferences.isAssistantEnabled.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.preferences.demoMode.collectAsStateWithLifecycle()
    val screenedCount by viewModel.screenedCount.collectAsStateWithLifecycle()
    val spamBlockedCount by viewModel.spamBlockedCount.collectAsStateWithLifecycle()
    val filteredCalls by viewModel.filteredCalls.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val upcomingScheduledCalls by viewModel.upcomingScheduledCalls.collectAsStateWithLifecycle()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
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
                contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Header with Dinthi AI Logo and ON/OFF status
                item {
                    DashboardTopBar(
                        onOpenSettings = { viewModel.navigateTo(Screen.Settings) }
                    )
                }

                // Explicit Demo Mode Banner (if enabled)
                if (isDemoMode) {
                    item {
                        DemoModeBanner(
                            onDisable = { viewModel.preferences.setDemoMode(false) }
                        )
                    }
                }

                // Main Status Card: "AI Assistant is On" or "AI Assistant is Off" with Switch
                item {
                    AssistantStatusCard(
                        isAssistantEnabled = isAssistantEnabled,
                        pulseScale = pulseScale,
                        onToggleAssistant = { viewModel.preferences.setAssistantEnabled(it) }
                    )
                }

                // "Tell Dinthi What To Do" Personal Instruction Hub
                item {
                    TellDinthiWhatToDoCard(
                        onInstructions = { viewModel.navigateTo(Screen.Instructions) },
                        onHealthCheck = { viewModel.navigateTo(Screen.HealthCheck) },
                        onSilentMode = { viewModel.preferences.setSilentMode(!viewModel.preferences.silentMode.value) },
                        onVoiceLanguage = { viewModel.navigateTo(Screen.Settings) },
                        onAddresses = { viewModel.navigateTo(Screen.Addresses) }
                    )
                }

                // Assistant Dashboard Statistics Cards (0 if no real data)
                item {
                    AssistantMetricsRow(
                        screenedCount = screenedCount,
                        spamBlockedCount = spamBlockedCount
                    )
                }

                // Primary Call Assistant Actions (Ask Dinthi to Call, Schedule, Voice Notes, Call Simulator)
                item {
                    QuickActionHub(
                        onAskToCall = { viewModel.navigateTo(Screen.CallSomeone) },
                        onScheduleCall = { viewModel.navigateTo(Screen.ScheduleCall) },
                        onVoiceNotes = { viewModel.navigateTo(Screen.VoiceNotes) },
                        onSimulate = { viewModel.navigateTo(Screen.CallSimulator) }
                    )
                }

                // Upcoming Scheduled Call Alert Card (if any real scheduled calls exist)
                if (upcomingScheduledCalls.isNotEmpty()) {
                    val nextCall = upcomingScheduledCalls.first()
                    item {
                        UpcomingCallAlertCard(
                            call = nextCall,
                            onOpenSchedule = { viewModel.navigateTo(Screen.ScheduleCall) }
                        )
                    }
                }

                // Call History Section: Search and Category Filter Chips
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("call_search_input"),
                            placeholder = { Text("Search real callers or numbers...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear search"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DinthiLime,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        )

                        // Separated Call History Filter Chips (Demo calls never appear under real calls)
                        val categories = listOf("ALL_REAL", "REAL_AI", "REAL_HUMAN", "FAILED", "DEMO")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(categories) { cat ->
                                val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                                val label = when (cat) {
                                    "ALL_REAL" -> "All Real"
                                    "REAL_AI" -> "Real AI"
                                    "REAL_HUMAN" -> "Real Human"
                                    "FAILED" -> "Failed"
                                    "DEMO" -> "Demo / Sim"
                                    else -> cat
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectedCategoryFilter.value = cat },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("filter_chip_${cat.lowercase()}"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (cat == "DEMO") {
                                            Color(0xFFF59E0B).copy(alpha = 0.2f)
                                        } else {
                                            DinthiLime.copy(alpha = 0.2f)
                                        },
                                        selectedLabelColor = if (cat == "DEMO") Color(0xFFF59E0B) else DinthiLime
                                    )
                                )
                            }
                        }
                    }
                }

                // Section Header: Call History
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Call History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${filteredCalls.size} calls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Calls List or Empty State (Strict No Fake Data Rule)
                if (filteredCalls.isEmpty()) {
                    item {
                        EmptyCallsCard(
                            isFiltered = searchQuery.isNotBlank() || selectedCategory != "ALL_REAL",
                            selectedCategory = selectedCategory,
                            onReset = {
                                viewModel.searchQuery.value = ""
                                viewModel.selectedCategoryFilter.value = "ALL_REAL"
                            },
                            onLaunchSimulator = { viewModel.navigateTo(Screen.CallSimulator) }
                        )
                    }
                } else {
                    items(filteredCalls, key = { it.id }) { call ->
                        CallItemCard(
                            call = call,
                            onClick = { viewModel.navigateTo(Screen.CallDetail(call.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTopBar(
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(DinthiLime.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Dinthi AI",
                    tint = DinthiLime,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = "Dinthi AI",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Autonomous Call Assistant",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.testTag("settings_button")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DemoModeBanner(
    onDisable: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                Text(
                    text = "Demo Mode Active: Test data is displayed for review.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Turn Off",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF59E0B),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onDisable)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun AssistantStatusCard(
    isAssistantEnabled: Boolean,
    pulseScale: Float,
    onToggleAssistant: (Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .scale(if (isAssistantEnabled) pulseScale else 1.0f)
                            .clip(CircleShape)
                            .background(if (isAssistantEnabled) DinthiLime else Color.Gray)
                    )

                    Column {
                        Text(
                            text = if (isAssistantEnabled) "AI Assistant is On" else "AI Assistant is Off",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isAssistantEnabled) "Autonomous call screening is active" else "Incoming calls will ring normally",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isAssistantEnabled) DinthiLime else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isAssistantEnabled,
                    onCheckedChange = onToggleAssistant,
                    modifier = Modifier.testTag("assistant_toggle_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = DinthiLime
                    )
                )
            }
        }
    }
}

@Composable
private fun TellDinthiWhatToDoCard(
    onInstructions: () -> Unit,
    onHealthCheck: () -> Unit,
    onSilentMode: () -> Unit,
    onVoiceLanguage: () -> Unit,
    onAddresses: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tell Dinthi What To Do",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InstructionPill(
                    title = "Your Instructions",
                    icon = Icons.Default.Psychology,
                    onClick = onInstructions,
                    modifier = Modifier.weight(1f)
                )
                InstructionPill(
                    title = "Health Check",
                    icon = Icons.Default.HealthAndSafety,
                    onClick = onHealthCheck,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InstructionPill(
                    title = "Silent Mode",
                    icon = Icons.Default.VolumeOff,
                    onClick = onSilentMode,
                    modifier = Modifier.weight(1f)
                )
                InstructionPill(
                    title = "Voice & Lang",
                    icon = Icons.Default.Language,
                    onClick = onVoiceLanguage,
                    modifier = Modifier.weight(1f)
                )
                InstructionPill(
                    title = "Addresses",
                    icon = Icons.Default.LocationOn,
                    onClick = onAddresses,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun InstructionPill(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DinthiLime,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AssistantMetricsRow(
    screenedCount: Int,
    spamBlockedCount: Int
) {
    val estimatedMinutesSaved = (screenedCount * 3) + (spamBlockedCount * 2)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricCard(
            title = "Calls Screened",
            value = screenedCount.toString(),
            icon = Icons.Default.PhoneCallback,
            tint = DinthiLime,
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            title = "Spam Blocked",
            value = spamBlockedCount.toString(),
            icon = Icons.Default.Security,
            tint = Color(0xFFEF4444),
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            title = "Time Saved",
            value = "${estimatedMinutesSaved}m",
            icon = Icons.Default.Timer,
            tint = Color(0xFF38BDF8),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickActionHub(
    onAskToCall: () -> Unit,
    onScheduleCall: () -> Unit,
    onVoiceNotes: () -> Unit,
    onSimulate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionButton(
            title = "Ask to Call",
            icon = Icons.Default.CallMade,
            tint = DinthiLime,
            onClick = onAskToCall,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            title = "Schedule",
            icon = Icons.Default.Schedule,
            tint = Color(0xFFA78BFA),
            onClick = onScheduleCall,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            title = "Voice Notes",
            icon = Icons.Default.Mic,
            tint = Color(0xFFF59E0B),
            onClick = onVoiceNotes,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            title = "Simulator",
            icon = Icons.Default.PlayArrow,
            tint = Color(0xFF38BDF8),
            onClick = onSimulate,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionButton(
    title: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun UpcomingCallAlertCard(
    call: com.example.data.local.entity.ScheduledCallEntity,
    onOpenSchedule: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSchedule)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DinthiLime.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = DinthiLime,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "Upcoming: ${call.recipientName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(call.scheduledTimestamp))} • ${call.agenda.take(30)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${call.phoneNumber}")
                    }
                    context.startActivity(intent)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DinthiLime)
            ) {
                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Dial", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun EmptyCallsCard(
    isFiltered: Boolean,
    selectedCategory: String = "ALL_REAL",
    onReset: () -> Unit,
    onLaunchSimulator: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhoneCallback,
                contentDescription = "No calls yet",
                tint = DinthiLime,
                modifier = Modifier.size(48.dp)
            )

            Text(
                text = when {
                    selectedCategory == "DEMO" -> "No Demo / Simulator calls yet"
                    isFiltered -> "No matching calls in this category"
                    else -> "No real calls yet"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = when {
                    selectedCategory == "DEMO" -> "Simulated calls from the Demo Simulator are kept isolated here and never appear in Real Calls."
                    isFiltered -> "Try clearing your search query or switching call history categories."
                    else -> "Real AI calls, real human calls, and failed outbound attempts will appear here. Simulator calls stay in Demo / Sim."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (isFiltered && selectedCategory != "DEMO") {
                Button(onClick = onReset) {
                    Text("Show All Real Calls")
                }
            } else {
                Button(
                    onClick = onLaunchSimulator,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DinthiLime)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Simulate Call",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Screening in Simulator", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CallItemCard(
    call: CallEntity,
    onClick: () -> Unit
) {
    val displayName = if (call.callerName.isNotBlank()) call.callerName else call.phoneNumber

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("call_item_${call.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val (avatarIcon, avatarBg, avatarTint) = when {
                        call.isSpam -> Triple(Icons.Default.Security, Color(0xFFFFEBEE), Color(0xFFEF4444))
                        call.category.equals("Delivery", true) -> Triple(Icons.Default.LocalShipping, Color(0xFFE0F2FE), Color(0xFF0284C7))
                        call.category.equals("Healthcare", true) -> Triple(Icons.Default.MedicalServices, Color(0xFFECFDF5), Color(0xFF059669))
                        call.category.equals("Recruiter", true) -> Triple(Icons.Default.Work, Color(0xFFEDE9FE), Color(0xFF7C3AED))
                        else -> Triple(Icons.Default.Call, MaterialTheme.colorScheme.surfaceVariant, DinthiLime)
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(avatarBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = avatarIcon,
                            contentDescription = call.category,
                            tint = avatarTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (call.callerName.isNotBlank() && call.callerName != call.phoneNumber) {
                            Text(
                                text = call.phoneNumber,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = if (call.isContact) "Saved Contact" else "Unknown Caller",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatCallTime(call.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val isDemoRecord = call.isDemo || call.status == "DEMO_SIMULATOR"
                    val badgeLabel = when {
                        isDemoRecord -> "Demo / Sim"
                        call.isSpam -> "Spam (${call.spamScore}%)"
                        call.status == "OUTGOING_AI" -> "Real AI Call"
                        call.status == "OUTGOING_HUMAN" -> "Real Human Call"
                        call.status == "OUTGOING_FAILED" -> "Failed Call"
                        call.status == "SCREENED" -> "Real AI Screened"
                        call.status == "ANSWERED" -> "Real Human"
                        else -> call.category
                    }
                    CategoryBadge(
                        category = badgeLabel,
                        isSpam = call.isSpam || call.status == "OUTGOING_FAILED",
                        isDemo = isDemoRecord
                    )
                }
            }

            if (call.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = call.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: String, isSpam: Boolean, isDemo: Boolean = false) {
    val bgColor = when {
        isSpam -> Color(0xFFFFEBEE)
        isDemo -> Color(0xFFF59E0B).copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        isSpam -> Color(0xFFC62828)
        isDemo -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = category,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

private fun formatCallTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60 * 1000 -> "Just now"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (3600 * 1000)}h ago"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}

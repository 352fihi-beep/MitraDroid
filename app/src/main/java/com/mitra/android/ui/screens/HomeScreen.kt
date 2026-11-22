package com.mitra.android.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mitra.android.ai.LocalModelStatus
import com.mitra.android.ui.HomeUiState

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    widthSizeClass: WindowWidthSizeClass,
    onNavigate: (String) -> Unit,
    onRefreshTelemetry: () -> Unit,
    onStartQuickCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshTelemetry()
    }

    val isExpanded = widthSizeClass != WindowWidthSizeClass.Compact

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                    slideInVertically(spring(stiffness = Spring.StiffnessLow)) { -24 }
            ) {
                HeroTelemetryBanner(
                    uiState = uiState,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRefreshTelemetry()
                    }
                )
            }
        }

        if (uiState.missingPermissions.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Telephony & Mic Permissions",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${uiState.missingPermissions.size} runtime permissions pending for live Telecom & SMS.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.RECORD_AUDIO,
                                        Manifest.permission.READ_PHONE_STATE,
                                        Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.RECEIVE_SMS,
                                        Manifest.permission.SEND_SMS,
                                        Manifest.permission.READ_SMS
                                    )
                                )
                            },
                            modifier = Modifier.testTag("grant_permissions_button")
                        ) {
                            Text("Grant")
                        }
                    }
                }
            }
        }

        if (isExpanded) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CapabilityTile(
                        icon = Icons.Default.Call,
                        title = "In-Call Console",
                        subtitle = "Live STT/TTS & IVR DTMF",
                        tag = "card_incall",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartQuickCall()
                        }
                    )
                    CapabilityTile(
                        icon = Icons.Default.Sms,
                        title = "SMS Threads",
                        subtitle = "${uiState.stats.totalSms} messages • ${uiState.stats.flaggedPhishingSms} flagged",
                        tag = "card_sms_threads",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigate("sms")
                        }
                    )
                    CapabilityTile(
                        icon = Icons.Default.History,
                        title = "Call History",
                        subtitle = "${uiState.stats.totalCalls} logged calls",
                        tag = "card_history",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigate("history")
                        }
                    )
                    CapabilityTile(
                        icon = Icons.Default.GraphicEq,
                        title = "Voice & Edge AI",
                        subtitle = when (val m = uiState.modelStatus) {
                            is LocalModelStatus.MediaPipeReady -> m.modelName
                            is LocalModelStatus.Loading -> "Loading..."
                            else -> "Rule Fallback Ready"
                        },
                        tag = "card_voice_studio",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigate("voiceTraining")
                        }
                    )
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CapabilityTile(
                        icon = Icons.Default.Call,
                        title = "In-Call Console",
                        subtitle = "Live IVR & DTMF",
                        tag = "card_incall",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartQuickCall()
                        }
                    )
                    CapabilityTile(
                        icon = Icons.Default.Sms,
                        title = "SMS Threads",
                        subtitle = "${uiState.stats.totalSms} messages",
                        tag = "card_sms_threads",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigate("sms")
                        }
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CapabilityTile(
                        icon = Icons.Default.History,
                        title = "Call History",
                        subtitle = "${uiState.stats.totalCalls} call logs",
                        tag = "card_history",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigate("history")
                        }
                    )
                    CapabilityTile(
                        icon = Icons.Default.GraphicEq,
                        title = "Voice & Edge AI",
                        subtitle = "MediaPipe + TTS",
                        tag = "card_voice_studio",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigate("voiceTraining")
                        }
                    )
                }
            }
        }

        item {
            Card(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigate("settings")
                },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_capabilities_settings")
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Capabilities and API Keys",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "API Keys, QoL Rules & Routing",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val keyStatus = if (uiState.settings.hasAnyCloudKey()) {
                            "Primary: ${uiState.settings.primaryProvider} (Key configured)"
                        } else {
                            "No cloud key yet — Edge Fallback active"
                        }
                        Text(
                            text = keyStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroTelemetryBanner(
    uiState: HomeUiState,
    onClick: () -> Unit
) {
    val battery = uiState.batteryState
    val isLowPower = battery.isLowPower(uiState.settings.lowBatteryThreshold)
    val isHot = battery.isOverheating(uiState.settings.thermalThresholdCelsius)
    val routeBadge = when {
        isHot -> "THERMAL GUARD"
        isLowPower -> "LOW-POWER EDGE"
        uiState.settings.hasAnyCloudKey() -> "CLOUD + EDGE HYBRID"
        else -> "ON-DEVICE EDGE"
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_telemetry_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mitra AI Hub",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = routeBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Text(
                    text = "Bilingual Telecom IVR, compressed Room memory, and MediaPipe edge fallback.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = onClick,
                        label = {
                            Text(
                                "${battery.level.toInt()}% • ${battery.temperatureCelsius}°C",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.BatteryChargingFull,
                                contentDescription = "Battery telemetry",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    AssistChip(
                        onClick = onClick,
                        label = {
                            Text(
                                if (uiState.settings.phishingGuardEnabled) "Shield ON" else "Shield OFF",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = "Phishing shield",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    AssistChip(
                        onClick = onClick,
                        label = {
                            Text(
                                if (uiState.modelStatus is LocalModelStatus.MediaPipeReady) "MediaPipe" else "Rule Edge",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Memory,
                                contentDescription = "Local engine status",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
            .height(148.dp)
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

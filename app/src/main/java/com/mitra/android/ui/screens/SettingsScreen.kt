package com.mitra.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mitra.android.LLMConfig
import com.mitra.android.data.local.UserSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    keyTestFeedback: Map<String, String>,
    onSaveApiKey: (String, String) -> Unit,
    onTestApiKey: (String, String) -> Unit,
    onSelectPrimaryProvider: (String) -> Unit,
    onUpdatePowerThresholds: (Int, Float) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectAccentTheme: (String) -> Unit,
    onToggleAutoReplySms: (Boolean) -> Unit,
    onTogglePhishingGuard: (Boolean) -> Unit,
    onToggleReadAloudSms: (Boolean) -> Unit,
    onToggleBlockUnknown: (Boolean) -> Unit,
    onToggleAutoMuteSpam: (Boolean) -> Unit,
    onToggleVibrateOnRing: (Boolean) -> Unit,
    onToggleDnd: (Boolean) -> Unit,
    onToggleSpeechToText: (Boolean) -> Unit,
    onAddWhitelist: (String) -> Unit,
    onRemoveWhitelist: (String) -> Unit,
    onClearAllKeys: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var whitelistInput by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Cloud AI Providers & Keys", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Keys stay locally on your device in DataStore Preferences. Choose your primary provider and optional fallbacks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LLMConfig.providers.keys.forEach { provider ->
                            FilterChip(
                                selected = settings.primaryProvider == provider,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectPrimaryProvider(provider)
                                },
                                label = { Text(provider) },
                                modifier = Modifier.testTag("provider_chip_$provider")
                            )
                        }
                    }

                    LLMConfig.providers.keys.forEach { provider ->
                        ProviderKeyEditor(
                            provider = provider,
                            initialKey = settings.apiKeyFor(provider),
                            testFeedback = keyTestFeedback[provider],
                            onSave = { key ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSaveApiKey(provider, key)
                            },
                            onTest = { key ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTestApiKey(provider, key)
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClearAllKeys()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("clear_api_keys_button")
                    ) {
                        Text("Rotate / Clear All Stored Keys")
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Battery & Thermal Edge Guard", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Low Battery Edge Threshold: ${settings.lowBatteryThreshold}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = settings.lowBatteryThreshold.toFloat(),
                        onValueChange = {
                            onUpdatePowerThresholds(it.roundToInt(), settings.thermalThresholdCelsius)
                        },
                        valueRange = 5f..50f,
                        modifier = Modifier.testTag("slider_battery_threshold")
                    )
                    Text(
                        "Thermal Guard Threshold: ${settings.thermalThresholdCelsius.roundToInt()}°C",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = settings.thermalThresholdCelsius,
                        onValueChange = {
                            onUpdatePowerThresholds(settings.lowBatteryThreshold, it)
                        },
                        valueRange = 30f..55f,
                        modifier = Modifier.testTag("slider_thermal_threshold")
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Quality-of-Life Automation", style = MaterialTheme.typography.titleMedium)
                    SettingToggleRow("Phishing SMS Shield", settings.phishingGuardEnabled, "toggle_phishing", onTogglePhishingGuard)
                    SettingToggleRow("Auto-Reply Incoming SMS", settings.autoReplySmsEnabled, "toggle_autoreply", onToggleAutoReplySms)
                    SettingToggleRow("Read Aloud Incoming SMS", settings.readAloudSmsEnabled, "toggle_read_aloud", onToggleReadAloudSms)
                    SettingToggleRow("Auto-Mute Spam & Screened Calls", settings.autoMuteSpamEnabled, "toggle_automute", onToggleAutoMuteSpam)
                    SettingToggleRow("Block Unknown Short Numbers", settings.blockUnknownEnabled, "toggle_block_unknown", onToggleBlockUnknown)
                    SettingToggleRow("Do Not Disturb Mode", settings.dndEnabled, "toggle_dnd", onToggleDnd)
                    SettingToggleRow("Vibrate on Ringing", settings.vibrateOnRingEnabled, "toggle_vibrate", onToggleVibrateOnRing)
                    SettingToggleRow("Live Speech-to-Text Engine", settings.speechToTextEnabled, "toggle_stt", onToggleSpeechToText)
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Appearance & Whitelist", style = MaterialTheme.typography.titleMedium)
                    SettingToggleRow("Dark Mode", settings.darkThemeEnabled, "toggle_dark_theme", onToggleDarkTheme)
                    SettingToggleRow("Material You Dynamic Color", settings.dynamicColorEnabled, "toggle_dynamic_color", onToggleDynamicColor)

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ElectricTeal", "CobaltPulse", "AmberEdge").forEach { palette ->
                            FilterChip(
                                selected = settings.accentTheme == palette,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectAccentTheme(palette)
                                },
                                label = { Text(palette) },
                                modifier = Modifier.testTag("theme_chip_$palette")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = whitelistInput,
                            onValueChange = { whitelistInput = it },
                            label = { Text("Whitelist number") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("whitelist_input")
                        )
                        Button(
                            onClick = {
                                if (whitelistInput.isNotBlank()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAddWhitelist(whitelistInput)
                                    whitelistInput = ""
                                }
                            },
                            modifier = Modifier.height(48.dp).testTag("whitelist_add_button")
                        ) {
                            Text("Add")
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        settings.whitelistedContacts.forEach { contact ->
                            InputChip(
                                selected = false,
                                onClick = { onRemoveWhitelist(contact) },
                                label = { Text(contact) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Remove $contact")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderKeyEditor(
    provider: String,
    initialKey: String,
    testFeedback: String?,
    onSave: (String) -> Unit,
    onTest: (String) -> Unit
) {
    var keyDraft by remember(initialKey) { mutableStateOf(initialKey) }
    var visible by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = keyDraft,
            onValueChange = { keyDraft = it },
            label = { Text("$provider API Key") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(
                    onClick = { visible = !visible },
                    modifier = Modifier.testTag("toggle_visibility_$provider")
                ) {
                    Icon(
                        if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle key visibility"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("api_key_input_$provider")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onSave(keyDraft) },
                modifier = Modifier.height(48.dp).testTag("save_key_button_$provider")
            ) {
                Text("Save")
            }
            OutlinedButton(
                onClick = { onTest(keyDraft) },
                modifier = Modifier.height(48.dp).testTag("test_key_button_$provider")
            ) {
                Text("Verify")
            }
            if (!testFeedback.isNullOrBlank()) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = testFeedback,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(it)
            },
            modifier = Modifier.testTag(tag)
        )
    }
}

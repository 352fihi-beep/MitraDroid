package com.mitra.android.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mitra.android.database.CallRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    calls: List<CallRecord>,
    onExportCsv: () -> Unit,
    onShowDailyReport: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedMode by rememberSaveable { mutableStateOf("ALL") }
    val modes = listOf("ALL", "CLOUD", "MEDIAPIPE", "RULE_FALLBACK")

    val filtered = calls.filter { record ->
        val matchesMode = selectedMode == "ALL" || record.inferenceMode == selectedMode
        val matchesQuery = searchQuery.isBlank() ||
            record.contactId.contains(searchQuery, ignoreCase = true) ||
            record.transcript.contains(searchQuery, ignoreCase = true)
        matchesMode && matchesQuery
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search transcripts or contacts") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search history") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_search_input")
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(modes) { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedMode = mode
                    },
                    label = { Text(mode, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("history_filter_$mode")
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onExportCsv()
                },
                modifier = Modifier.weight(1f).height(48.dp).testTag("export_csv_button")
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
                Text(" CSV", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onShowDailyReport()
                },
                modifier = Modifier.weight(1f).height(48.dp).testTag("daily_report_button")
            ) {
                Icon(Icons.Default.Assessment, contentDescription = "Daily report")
                Text(" Report", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClearHistory()
                },
                modifier = Modifier.weight(1f).height(48.dp).testTag("clear_history_button")
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Clear history")
                Text(" Clear", style = MaterialTheme.typography.labelMedium)
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching call logs yet. Start a call from the Hub or In-Call Console.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.id }) { call ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(call.contactId, style = MaterialTheme.typography.titleMedium)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = call.inferenceMode,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${call.direction} • ${call.durationSeconds}s • ${formatter.format(Date(call.timestamp))}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (call.dtmfHistory.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "DTMF Tones: ${call.dtmfHistory}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            if (call.transcript.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = call.transcript,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 4
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

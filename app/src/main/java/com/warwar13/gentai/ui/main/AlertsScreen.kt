package com.warwar13.gentai.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.warwar13.gentai.data.AlertEventEntity
import com.warwar13.gentai.data.TelemetryDatabase
import com.warwar13.gentai.monitor.AlertHistoryRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val AlertsDarkColors = darkColorScheme(
    primary = Color(0xFFA8F05A),
    onPrimary = Color(0xFF142000),
    primaryContainer = Color(0xFF203A18),
    onPrimaryContainer = Color(0xFFD6FFB5),
    secondary = Color(0xFF65D6A6),
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF164A3B),
    onSecondaryContainer = Color(0xFF9BF6D1),
    tertiary = Color(0xFFFFC857),
    background = Color(0xFF070A08),
    onBackground = Color(0xFFF1F7ED),
    surface = Color(0xFF0E130F),
    onSurface = Color(0xFFF1F7ED),
    surfaceVariant = Color(0xFF202820),
    onSurfaceVariant = Color(0xFFADB8A9),
    outline = Color(0xFF667063),
    error = Color(0xFFFF6B6B),
)

internal class AlertsViewModel(private val repository: AlertHistoryRepository) : ViewModel() {
    val alerts = repository.observeLastMonth().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repository.prune() }
    }
}

private enum class AlertFilter(val label: String) {
    ALL("All"),
    VOLTAGE("Voltage"),
    CURRENT("Current"),
    TEMPERATURE("Temperature"),
    OTHER("Other"),
}

private fun AlertEventEntity.category(): AlertFilter {
    val value = "${title} ${message}".lowercase()
    return when {
        "volt" in value -> AlertFilter.VOLTAGE
        "current" in value || "short" in value -> AlertFilter.CURRENT
        "temp" in value || "thermal" in value -> AlertFilter.TEMPERATURE
        else -> AlertFilter.OTHER
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val alertsViewModel: AlertsViewModel = viewModel {
        AlertsViewModel(AlertHistoryRepository(TelemetryDatabase.get(context)))
    }
    val alerts by alertsViewModel.alerts.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(AlertFilter.ALL) }
    val filtered = remember(alerts, filter) {
        alerts.filter { filter == AlertFilter.ALL || it.category() == filter }
    }
    MaterialTheme(colorScheme = AlertsDarkColors) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Alerts", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AlertsDarkColors.background),
                )
            },
        ) { innerPadding ->
            LazyColumn(
                Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("BMS warnings", fontWeight = FontWeight.Bold)
                                Text(
                                    "Only protection events reported by the battery are shown here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AlertFilter.entries) { option ->
                            FilterChip(
                                selected = filter == option,
                                onClick = { filter = option },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
                item {
                    Text(
                        "${filtered.size} warning${if (filtered.size == 1) "" else "s"} · last 30 days",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (filtered.isEmpty()) {
                    item { EmptyAlertsCard() }
                } else {
                    items(filtered, key = { it.id }) { alert -> AlertCard(alert) }
                }
            }
        }
    }
}

@Composable
private fun EmptyAlertsCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("No warnings", fontWeight = FontWeight.Bold)
                Text(
                    "Your BMS has not reported a protection event in this period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AlertCard(alert: AlertEventEntity) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(alert.category().label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(alert.message)
                Text(
                    formatAlertTime(alert.timestampEpochMs) + (alert.socPct?.let { " · $it% SOC" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val alertTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
private fun formatAlertTime(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(alertTimeFormatter)

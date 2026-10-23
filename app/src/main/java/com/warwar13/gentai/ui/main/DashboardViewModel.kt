package com.warwar13.gentai.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warwar13.gentai.analytics.TelemetryAnalytics
import com.warwar13.gentai.data.DailySummaryEntity
import com.warwar13.gentai.data.GentaiTelemetryRepository
import com.warwar13.gentai.data.PowerSessionEntity
import com.warwar13.gentai.data.TelemetrySampleEntity
import com.warwar13.gentai.monitor.MonitorService
import com.warwar13.gentai.monitor.MonitorState
import com.warwar13.gentai.monitor.ChargingCellPolicy
import com.warwar13.gentai.monitor.ChargingCellSeverity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val connection: MonitorState = MonitorState.Idle,
    val latest: TelemetrySampleEntity? = null,
    val timeline: List<TelemetrySampleEntity> = emptyList(),
    val sessions: List<PowerSessionEntity> = emptyList(),
    val daily: List<DailySummaryEntity> = emptyList(),
)

class DashboardViewModel(repository: GentaiTelemetryRepository) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = combine(
        MonitorService.state, MonitorService.liveSample, repository.recentSamples(), repository.recentPowerSessions(), repository.recentDailySummaries(),
    ) { connection, liveSample, newestFirst, sessions, daily ->
        val latest = if (connection is MonitorState.Connected) liveSample ?: newestFirst.firstOrNull() else newestFirst.firstOrNull()
        DashboardUiState(connection, latest, newestFirst.asReversed(), sessions, daily)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}

fun TelemetrySampleEntity.displayMode() = TelemetryAnalytics.modeFor(currentA).name.lowercase()
fun TelemetrySampleEntity.cells(): List<Double> = cellVoltagesV.split(',').mapNotNull(String::toDoubleOrNull)
fun TelemetrySampleEntity.probes(): List<Int> = probeTemperaturesC.split(',').mapNotNull(String::toIntOrNull)

enum class CellBalanceState { BALANCED, DIFFERENT, ALERT, MISSING }

fun cellBalanceStates(cellsV: List<Double>): List<CellBalanceState> {
    if (cellsV.isEmpty()) return emptyList()
    val status = ChargingCellPolicy.status(cellsV)
    val highestIndex = status?.highCellNumber?.minus(1)
    return cellsV.mapIndexed { index, value ->
        when {
            !value.isFinite() || value <= 0.0 -> CellBalanceState.MISSING
            value >= ChargingCellPolicy.RED_CELL_V -> CellBalanceState.ALERT
            value >= ChargingCellPolicy.YELLOW_CELL_V -> CellBalanceState.DIFFERENT
            index == highestIndex && status.severity == ChargingCellSeverity.RED -> CellBalanceState.ALERT
            index == highestIndex && status.severity == ChargingCellSeverity.YELLOW -> CellBalanceState.DIFFERENT
            else -> CellBalanceState.BALANCED
        }
    }
}

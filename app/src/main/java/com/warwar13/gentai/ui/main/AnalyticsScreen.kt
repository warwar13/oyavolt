package com.warwar13.gentai.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warwar13.gentai.analytics.AnalyticsSample
import com.warwar13.gentai.analytics.BatteryUsageCycle
import com.warwar13.gentai.analytics.DirectionalAnalytics
import com.warwar13.gentai.analytics.DirectionalDayInsights
import com.warwar13.gentai.analytics.EnergySettings
import com.warwar13.gentai.analytics.UsageAnalytics
import com.warwar13.gentai.analytics.UsageInsights
import com.warwar13.gentai.data.GentaiTelemetryRepository
import com.warwar13.gentai.data.SolarDaySnapshot
import com.warwar13.gentai.data.SolarEnergyRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

internal data class DayWindow(val startEpochMs: Long, val endEpochMs: Long)
internal fun LocalDate.dayWindow(zoneId: ZoneId): DayWindow = DayWindow(
    atStartOfDay(zoneId).toInstant().toEpochMilli(),
    plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1,
)
internal fun batteryUsageAsOf(window: DayWindow, nowEpochMs: Long): Long = minOf(window.endEpochMs, nowEpochMs)

internal data class AnalyticsUiState(
    val date: LocalDate? = null,
    val settings: EnergySettings? = null,
    val snapshot: SolarDaySnapshot? = null,
    val samples: List<AnalyticsSample> = emptyList(),
    val insights: UsageInsights = UsageInsights(),
    val directional: DirectionalDayInsights = DirectionalDayInsights(),
    val batteryUsage: BatteryUsageCycle? = null,
    val nowMs: Long = System.currentTimeMillis(),
    val error: String? = null,
)
internal data class AnalyticsRequest(val date: LocalDate, val settings: EnergySettings)

@OptIn(ExperimentalCoroutinesApi::class)
internal class AnalyticsViewModel(
    private val repository: GentaiTelemetryRepository,
    private val solarRepository: SolarEnergyRepository,
) : ViewModel() {
    private val request = MutableStateFlow<AnalyticsRequest?>(null)
    val uiState = request.flatMapLatest { selected ->
        flow {
            if (selected == null) return@flow
            emit(AnalyticsUiState(date = selected.date, settings = selected.settings))
            do {
                try {
                    val now = System.currentTimeMillis()
                    val snapshot = solarRepository.daySnapshot(selected.date, selected.settings, now)
                    val analysed = withContext(Dispatchers.Default) {
                        val samples = snapshot.samples.mapIndexed { index, row ->
                            val previous = snapshot.samples.getOrNull(index - 1)
                            AnalyticsSample(
                                row.timestampEpochMs, row.voltageV, row.currentA, row.socPct,
                                (row.probeTemperaturesC.split(',').mapNotNull(String::toIntOrNull) + listOf(row.ambientTemperatureC, row.mosTemperatureC)).maxOrNull(),
                                row.cellDeltaV,
                                row.gapFromPreviousMs != null || (previous != null && previous.connectionSessionId != row.connectionSessionId),
                            )
                        }
                        AnalyticsUiState(
                            selected.date, selected.settings, snapshot, samples,
                            UsageAnalytics.analyse(samples), DirectionalAnalytics.analyse(samples), nowMs = now,
                        )
                    }
                    emit(analysed.copy(batteryUsage = repository.batteryUsageCycle(batteryUsageAsOf(selected.date.dayWindow(selected.settings.zone), now))))
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emit(AnalyticsUiState(date = selected.date, settings = selected.settings, error = "Could not load this day's readings. Retrying shortly."))
                }
                delay(60_000)
            } while (currentCoroutineContext().isActive)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    fun select(date: LocalDate, settings: EnergySettings) { request.value = AnalyticsRequest(date, settings) }
    fun stopDayQueries() { request.value = null }
}

internal data class BatteryUsageProjectionDisplay(val value: String, val hint: String? = null)
internal fun batteryUsageProjectionDisplay(cycle: BatteryUsageCycle): BatteryUsageProjectionDisplay {
    if (cycle.completed) return BatteryUsageProjectionDisplay("Complete")
    cycle.projectedActiveMsTo20?.let { return BatteryUsageProjectionDisplay("~${duration(it)}") }
    if (cycle.dischargingMs <= 0L) return BatteryUsageProjectionDisplay("Waiting", "Start discharging to build an active-use estimate.")
    val remainingMs = (com.warwar13.gentai.analytics.BatteryUsageAccumulator.MIN_DISCHARGE_MS_FOR_ESTIMATE - cycle.dischargingMs).coerceAtLeast(0L)
    if (remainingMs > 0L) return BatteryUsageProjectionDisplay("${(remainingMs + 59_999L) / 60_000L}m more", "Estimate appears after 10 minutes of measured discharge; gaps are excluded.")
    return BatteryUsageProjectionDisplay("Waiting", "More valid discharge data is needed; gaps and charging are excluded.")
}


private fun duration(ms: Long): String {
    val minutes = ms.coerceAtLeast(0L) / 60_000L
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

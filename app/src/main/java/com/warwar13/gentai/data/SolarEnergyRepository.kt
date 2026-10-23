package com.warwar13.gentai.data

import androidx.room.withTransaction
import com.warwar13.gentai.analytics.EnergySettings
import com.warwar13.gentai.analytics.SolarChartBar
import com.warwar13.gentai.analytics.SolarHourlyReport
import com.warwar13.gentai.analytics.SolarReading
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class SolarDaySnapshot(
    val samples: List<TelemetrySampleEntity>,
    val hourly: List<SolarChartBar>,
    val daily: SolarDailyRollupEntity?,
)

class SolarEnergyRepository(private val database: TelemetryDatabase) {
    fun observeDays(): Flow<List<SolarDailyRollupEntity>> = database.telemetryDao().observeSolarDays()

    suspend fun daySnapshot(date: LocalDate, settings: EnergySettings, nowMs: Long): SolarDaySnapshot {
        val start = date.atStartOfDay(settings.zone).toInstant().toEpochMilli()
        val end = minOf(date.plusDays(1).atStartOfDay(settings.zone).toInstant().toEpochMilli(), nowMs)
        val (rows, daily) = database.withTransaction {
            val dao = database.telemetryDao()
            val rows = if (end > maxOf(start, settings.trackingStartMs)) {
                dao.recordedSamplesBetween(maxOf(start - 30_000, settings.trackingStartMs - 30_000), minOf(end + 30_000, nowMs))
            } else emptyList()
            rows to dao.solarDay(date.toString())
        }
        return withContext(Dispatchers.Default) {
            SolarDaySnapshot(
                rows.filter { it.timestampEpochMs >= maxOf(start, settings.trackingStartMs) && it.timestampEpochMs < end },
                SolarHourlyReport.make(rows.map { SolarReading(it.timestampEpochMs, it.powerW, it.connectionSessionId, it.gapFromPreviousMs != null) }, date, settings, nowMs),
                daily,
            )
        }
    }

    suspend fun hourlyFor(date: LocalDate, settings: EnergySettings, nowMs: Long): List<SolarChartBar> = daySnapshot(date, settings, nowMs).hourly
}

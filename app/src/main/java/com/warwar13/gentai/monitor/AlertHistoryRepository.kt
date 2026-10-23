package com.warwar13.gentai.monitor

import androidx.room.withTransaction
import com.warwar13.gentai.data.AlertEventEntity
import com.warwar13.gentai.data.TelemetryDatabase
import kotlinx.coroutines.flow.Flow

class AlertHistoryRepository(private val database: TelemetryDatabase) {
    fun observeLastMonth(nowEpochMs: Long = System.currentTimeMillis()): Flow<List<AlertEventEntity>> =
        database.telemetryDao().observeBmsAlertEventsSince(nowEpochMs - RETENTION_MS)

    suspend fun record(alerts: List<BmsAlert>, socPct: Int, nowEpochMs: Long = System.currentTimeMillis()) {
        val events = alerts.toHistoryEvents(socPct, nowEpochMs)
        if (events.isEmpty()) return
        database.withTransaction {
            database.telemetryDao().pruneNonBmsAlertEvents()
            database.telemetryDao().pruneAlertEvents(nowEpochMs - RETENTION_MS)
            database.telemetryDao().insertAlertEvents(events)
        }
    }

    suspend fun prune(nowEpochMs: Long = System.currentTimeMillis()) = database.withTransaction {
        database.telemetryDao().pruneNonBmsAlertEvents()
        database.telemetryDao().pruneAlertEvents(nowEpochMs - RETENTION_MS)
    }

    companion object {
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}

internal fun List<BmsAlert>.toHistoryEvents(socPct: Int, nowEpochMs: Long): List<AlertEventEntity> = flatMap { alert ->
    when (alert) {
        is BmsAlert.ProtectionWarnings -> alert.warnings.map { warning ->
            AlertEventEntity(
                timestampEpochMs = nowEpochMs,
                type = "BMS_PROTECTION",
                title = "BMS protection warning",
                message = warning,
                socPct = socPct,
            )
        }
        else -> emptyList()
    }
}

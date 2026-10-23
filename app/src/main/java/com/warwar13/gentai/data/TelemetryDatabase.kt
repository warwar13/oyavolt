package com.warwar13.gentai.data

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.warwar13.gentai.analytics.AnalyticsSample
import com.warwar13.gentai.analytics.BatteryUsageAccumulator
import com.warwar13.gentai.analytics.BatteryUsageCycle
import com.warwar13.gentai.analytics.BatteryUsageSample
import com.warwar13.gentai.analytics.NearFullPeakTracker
import com.warwar13.gentai.analytics.TelemetryAnalytics
import com.warwar13.gentai.analytics.EnergySettings
import com.warwar13.gentai.analytics.FlowRecordingAnalytics
import com.warwar13.gentai.analytics.FlowRecordingMode
import com.warwar13.gentai.analytics.FlowSample
import com.warwar13.gentai.analytics.SolarEnergyAnalytics
import com.warwar13.gentai.analytics.SolarEnergyAmount
import com.warwar13.gentai.analytics.SolarReading
import com.warwar13.gentai.protocol.BatteryInfo
import com.warwar13.gentai.protocol.RunningStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

@Entity(tableName = "connection_sessions")
data class ConnectionSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val endReason: String? = null,
    @ColumnInfo(defaultValue = "'RECORDED'") val origin: String = "RECORDED",
)

@Entity(tableName = "solar_daily_rollups")
data class SolarDailyRollupEntity(
    @PrimaryKey val localDate: String,
    val chargedWh: Double,
    val usedWh: Double,
    val observedMs: Long,
    val chargingMs: Long = 0,
    val dischargingMs: Long = 0,
)

@Entity(tableName = "monitoring_periods", indices = [Index("startedAtMs"), Index("endedAtMs")])
data class MonitoringPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val endReason: String? = null,
)

@Entity(tableName = "flow_recordings", indices = [Index("startedAtMs"), Index("mode")])
data class FlowRecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val endReason: String? = null,
    val chargedAh: Double = 0.0,
    val dischargedAh: Double = 0.0,
    val chargedWh: Double = 0.0,
    val dischargedWh: Double = 0.0,
    val observedMs: Long = 0,
    val missingMs: Long = 0,
    val sampleCount: Int = 0,
    val averagePowerW: Double = 0.0,
    val peakPowerW: Double = 0.0,
    val startSocPct: Int? = null,
    val endSocPct: Int? = null,
) {
    val coveragePct: Double get() = if (observedMs + missingMs == 0L) 0.0 else observedMs * 100.0 / (observedMs + missingMs)
}

@Entity(
    tableName = "telemetry_samples",
    foreignKeys = [ForeignKey(entity = ConnectionSessionEntity::class, parentColumns = ["id"], childColumns = ["connectionSessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("connectionSessionId"), Index("timestampEpochMs"), Index("recordingId")],
)
data class TelemetrySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMs: Long,
    val connectionSessionId: Long,
    val voltageV: Double,
    val currentA: Double,
    val powerW: Double,
    val socPct: Int,
    val sohPct: Int,
    val remainingAh: Double,
    val fullAh: Double,
    val cycleCount: Int,
    val cellVoltagesV: String,
    val cellMinV: Double?,
    val cellMaxV: Double?,
    val cellDeltaV: Double?,
    val probeTemperaturesC: String,
    val ambientTemperatureC: Int,
    val mosTemperatureC: Int,
    val protectionBitfield: Long? = null,
    val balanceMask: Long? = null,
    val disconnectedCellMask: Int? = null,
    val rssi: Int? = null,
    val qualityFlags: Long = 0,
    val gapFromPreviousMs: Long? = null,
    val recordingId: Long? = null,
)

@Entity(tableName = "power_sessions", indices = [Index("startEpochMs")])
data class PowerSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val mode: String, val startEpochMs: Long, val endEpochMs: Long,
    val startSocPct: Int?, val endSocPct: Int?, val chargedAh: Double, val dischargedAh: Double, val chargedWh: Double, val dischargedWh: Double,
    val averagePowerW: Double, val peakAbsolutePowerW: Double, val sampleCount: Int, val coveragePct: Double,
)

@Entity(tableName = "daily_summaries")
data class DailySummaryEntity(
    @PrimaryKey val dayStartEpochMs: Long, val chargedAh: Double, val dischargedAh: Double, val chargedWh: Double, val dischargedWh: Double,
    val observedMs: Long, val missingMs: Long, val coveragePct: Double, val sampleCount: Int,
)

@Entity(tableName = "alert_events", indices = [Index("timestampEpochMs")])
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMs: Long,
    val type: String,
    val title: String,
    val message: String,
    val socPct: Int? = null,
)

data class TelemetryChartPoint(
    val timestampEpochMs: Long,
    val endEpochMs: Long,
    val sampleCount: Int,
    val gapCount: Int,
    val chargingCount: Int,
    val dischargingCount: Int,
    val connectionCount: Int,
    val powerW: Double,
    val minPowerW: Double,
    val maxPowerW: Double,
    val voltageV: Double,
    val minVoltageV: Double,
    val maxVoltageV: Double,
    val currentA: Double,
    val minCurrentA: Double,
    val maxCurrentA: Double,
    val socPct: Double,
    val minSocPct: Double,
    val maxSocPct: Double,
    val cellDeltaV: Double?,
    val minCellDeltaV: Double?,
    val maxCellDeltaV: Double?,
    val mosTemperatureC: Double,
    val minMosTemperatureC: Double,
    val maxMosTemperatureC: Double,
    val ambientTemperatureC: Double,
    val minAmbientTemperatureC: Double,
    val maxAmbientTemperatureC: Double,
)

data class RecordingChartPoint(
    val timestampEpochMs: Long,
    val endEpochMs: Long,
    val sampleCount: Int,
    val gapCount: Int,
    val powerW: Double,
    val minPowerW: Double,
    val maxPowerW: Double,
    val currentA: Double,
    val minCurrentA: Double,
    val maxCurrentA: Double,
    val socPct: Double,
    val minSocPct: Double,
    val maxSocPct: Double,
)

data class RecordingHistoryItem(
    val id: Long,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val endReason: String?,
    val firstSampleAtEpochMs: Long?,
    val lastSampleAtEpochMs: Long?,
    val sampleCount: Int,
    val startSocPct: Int?,
    val endSocPct: Int?,
    val averageAbsolutePowerW: Double?,
    val peakAbsolutePowerW: Double?,
    val minimumVoltageV: Double?,
    val maximumTemperatureC: Int?,
)
@Entity(tableName = "capacity_tests") data class CapacityTestEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val state: String, val startedAtMs: Long, val pausedAtMs: Long? = null, val endedAtMs: Long? = null, val segment: Int = 0)
@Entity(tableName = "capacity_test_samples", foreignKeys = [ForeignKey(entity = CapacityTestEntity::class, parentColumns = ["id"], childColumns = ["testId"], onDelete = ForeignKey.CASCADE)], indices = [Index("testId")]) data class CapacityTestSampleEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val testId: Long, val timestampMs: Long, val voltageV: Double, val currentA: Double, val socPct: Int, val segment: Int = 0)

@Dao
interface TelemetryDao {
    @Insert suspend fun insertFlowRecording(recording: FlowRecordingEntity): Long
    @Query("UPDATE flow_recordings SET endedAtMs=:endedAtMs, endReason=:endReason, chargedAh=:chargedAh, dischargedAh=:dischargedAh, chargedWh=:chargedWh, dischargedWh=:dischargedWh, observedMs=:observedMs, missingMs=:missingMs, sampleCount=:sampleCount, averagePowerW=:averagePowerW, peakPowerW=:peakPowerW, startSocPct=:startSocPct, endSocPct=:endSocPct WHERE id=:id")
    suspend fun closeFlowRecording(id: Long, endedAtMs: Long, endReason: String?, chargedAh: Double, dischargedAh: Double, chargedWh: Double, dischargedWh: Double, observedMs: Long, missingMs: Long, sampleCount: Int, averagePowerW: Double, peakPowerW: Double, startSocPct: Int?, endSocPct: Int?)
    @Query("UPDATE flow_recordings SET chargedAh=:chargedAh, dischargedAh=:dischargedAh, chargedWh=:chargedWh, dischargedWh=:dischargedWh, observedMs=:observedMs, missingMs=:missingMs, sampleCount=:sampleCount, averagePowerW=:averagePowerW, peakPowerW=:peakPowerW, startSocPct=:startSocPct, endSocPct=:endSocPct WHERE id=:id")
    suspend fun updateFlowRecordingSummary(id: Long, chargedAh: Double, dischargedAh: Double, chargedWh: Double, dischargedWh: Double, observedMs: Long, missingMs: Long, sampleCount: Int, averagePowerW: Double, peakPowerW: Double, startSocPct: Int?, endSocPct: Int?)
    @Query("SELECT * FROM flow_recordings WHERE id=:id LIMIT 1") suspend fun flowRecording(id: Long): FlowRecordingEntity?
    @Query("SELECT * FROM flow_recordings WHERE id=:id LIMIT 1") fun observeFlowRecording(id: Long): Flow<FlowRecordingEntity?>
    @Query("SELECT * FROM flow_recordings ORDER BY startedAtMs, id") suspend fun allFlowRecordings(): List<FlowRecordingEntity>
    @Query("SELECT * FROM flow_recordings WHERE endedAtMs IS NULL ORDER BY startedAtMs, id") suspend fun openFlowRecordings(): List<FlowRecordingEntity>
    @Query("SELECT * FROM flow_recordings WHERE startedAtMs <= :endEpochMs AND COALESCE(endedAtMs, :endEpochMs) >= :startEpochMs ORDER BY startedAtMs DESC LIMIT :limit") fun observeFlowRecordingsBetween(startEpochMs: Long, endEpochMs: Long, limit: Int): Flow<List<FlowRecordingEntity>>
    @Query("SELECT COUNT(*) FROM flow_recordings WHERE startedAtMs <= :endEpochMs AND COALESCE(endedAtMs, :endEpochMs) >= :startEpochMs") fun observeFlowRecordingCount(startEpochMs: Long, endEpochMs: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM flow_recordings WHERE startedAtMs <= :endEpochMs AND COALESCE(endedAtMs, :endEpochMs) >= :startEpochMs AND (:mode IS NULL OR mode=:mode)") fun observeDayFlowRecordingCount(startEpochMs: Long, endEpochMs: Long, mode: String?): Flow<Int>
    @Query("SELECT * FROM flow_recordings WHERE startedAtMs <= :endEpochMs AND COALESCE(endedAtMs, :endEpochMs) >= :startEpochMs AND (:mode IS NULL OR mode=:mode) ORDER BY startedAtMs DESC LIMIT :limit") fun observeDayFlowRecordings(startEpochMs: Long, endEpochMs: Long, mode: String?, limit: Int): Flow<List<FlowRecordingEntity>>
    @Query("SELECT * FROM telemetry_samples WHERE recordingId=:recordingId ORDER BY timestampEpochMs, id") suspend fun flowRecordingSamples(recordingId: Long): List<TelemetrySampleEntity>
    @Query("SELECT * FROM telemetry_samples WHERE recordingId=:recordingId ORDER BY timestampEpochMs, id") fun observeFlowRecordingSamples(recordingId: Long): Flow<List<TelemetrySampleEntity>>
    @Query("""
        SELECT MIN(timestampEpochMs) AS timestampEpochMs, MAX(timestampEpochMs) AS endEpochMs,
               COUNT(*) AS sampleCount,
               SUM(CASE WHEN gapFromPreviousMs IS NOT NULL THEN 1 ELSE 0 END) AS gapCount,
               AVG(powerW) AS powerW, MIN(powerW) AS minPowerW, MAX(powerW) AS maxPowerW,
               AVG(currentA) AS currentA, MIN(currentA) AS minCurrentA, MAX(currentA) AS maxCurrentA,
               AVG(socPct) AS socPct, MIN(socPct) AS minSocPct, MAX(socPct) AS maxSocPct
        FROM telemetry_samples
        WHERE recordingId=:recordingId AND timestampEpochMs BETWEEN :startEpochMs AND :endEpochMs
        GROUP BY ((timestampEpochMs - :startEpochMs) / :bucketMs)
        ORDER BY timestampEpochMs
    """) fun observeRecordingChart(recordingId: Long, startEpochMs: Long, endEpochMs: Long, bucketMs: Long): Flow<List<RecordingChartPoint>>
    @Query("DELETE FROM flow_recordings WHERE id=:id") suspend fun deleteFlowRecording(id: Long)
    @Query("DELETE FROM flow_recordings") suspend fun clearFlowRecordings()
    @Insert suspend fun insertSession(session: ConnectionSessionEntity): Long
    @Insert suspend fun insertMonitoringPeriod(period: MonitoringPeriodEntity): Long
    @Query("SELECT * FROM monitoring_periods WHERE endedAtMs IS NULL ORDER BY startedAtMs DESC, id DESC LIMIT 1") suspend fun openMonitoringPeriod(): MonitoringPeriodEntity?
    @Query("UPDATE monitoring_periods SET endedAtMs=:endedAtMs, endReason=:reason WHERE endedAtMs IS NULL") suspend fun closeOpenMonitoringPeriods(endedAtMs: Long, reason: String)
    @Query("SELECT * FROM monitoring_periods ORDER BY startedAtMs DESC, id DESC LIMIT :limit") fun observeMonitoringPeriods(limit: Int): Flow<List<MonitoringPeriodEntity>>
    @Query("UPDATE connection_sessions SET endedAtEpochMs = :endedAt, endReason = :reason WHERE id = :id") suspend fun closeSession(id: Long, endedAt: Long, reason: String)
    @Query("UPDATE connection_sessions SET endedAtEpochMs = :endedAt, endReason = :reason WHERE endedAtEpochMs IS NULL") suspend fun closeOpenSessions(endedAt: Long, reason: String)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSample(sample: TelemetrySampleEntity): Long
    @Query("UPDATE telemetry_samples SET recordingId=:recordingId WHERE id=:sampleId") suspend fun assignSampleToRecording(sampleId: Long, recordingId: Long)
    @Query("SELECT * FROM telemetry_samples WHERE id < :sampleId ORDER BY id DESC LIMIT 1") suspend fun sampleBefore(sampleId: Long): TelemetrySampleEntity?
    @Query("SELECT * FROM telemetry_samples WHERE id=:sampleId LIMIT 1") suspend fun sample(sampleId: Long): TelemetrySampleEntity?
    @Query("UPDATE telemetry_samples SET gapFromPreviousMs=:gapMs WHERE id=:sampleId AND gapFromPreviousMs IS NULL") suspend fun markSampleGap(sampleId: Long, gapMs: Long)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSamples(samples: List<TelemetrySampleEntity>)
    @Query("SELECT * FROM connection_sessions WHERE startedAtEpochMs=:startedAtEpochMs LIMIT 1") suspend fun connectionSessionAt(startedAtEpochMs: Long): ConnectionSessionEntity?
    @Query("SELECT * FROM telemetry_samples ORDER BY timestampEpochMs DESC") fun observeSamples(): Flow<List<TelemetrySampleEntity>>
    @Query("SELECT * FROM telemetry_samples ORDER BY timestampEpochMs DESC LIMIT :limit") fun observeRecentSamples(limit: Int): Flow<List<TelemetrySampleEntity>>
    @Query("SELECT * FROM power_sessions ORDER BY startEpochMs DESC LIMIT :limit") fun observeRecentPowerSessions(limit: Int): Flow<List<PowerSessionEntity>>
    @Query("SELECT * FROM daily_summaries ORDER BY dayStartEpochMs DESC LIMIT :limit") fun observeRecentDailySummaries(limit: Int): Flow<List<DailySummaryEntity>>
    @Insert suspend fun insertAlertEvents(events: List<AlertEventEntity>)
    @Query("SELECT * FROM alert_events WHERE timestampEpochMs >= :sinceEpochMs ORDER BY timestampEpochMs DESC") fun observeAlertEventsSince(sinceEpochMs: Long): Flow<List<AlertEventEntity>>
    @Query("SELECT * FROM alert_events WHERE type = 'BMS_PROTECTION' AND timestampEpochMs >= :sinceEpochMs ORDER BY timestampEpochMs DESC") fun observeBmsAlertEventsSince(sinceEpochMs: Long): Flow<List<AlertEventEntity>>
    @Query("DELETE FROM alert_events WHERE type != 'BMS_PROTECTION'") suspend fun pruneNonBmsAlertEvents()
    @Query("DELETE FROM alert_events WHERE timestampEpochMs < :cutoffEpochMs") suspend fun pruneAlertEvents(cutoffEpochMs: Long)
    @Query("""
        SELECT MIN(timestampEpochMs) AS timestampEpochMs,
               MAX(timestampEpochMs) AS endEpochMs,
               COUNT(*) AS sampleCount,
               SUM(CASE WHEN gapFromPreviousMs IS NOT NULL THEN 1 ELSE 0 END) AS gapCount,
               SUM(CASE WHEN currentA > 0.15 THEN 1 ELSE 0 END) AS chargingCount,
               SUM(CASE WHEN currentA < -0.15 THEN 1 ELSE 0 END) AS dischargingCount,
               COUNT(DISTINCT connectionSessionId) AS connectionCount,
               AVG(powerW) AS powerW,
               MIN(powerW) AS minPowerW,
               MAX(powerW) AS maxPowerW,
               AVG(voltageV) AS voltageV,
               MIN(voltageV) AS minVoltageV,
               MAX(voltageV) AS maxVoltageV,
               AVG(currentA) AS currentA,
               MIN(currentA) AS minCurrentA,
               MAX(currentA) AS maxCurrentA,
               AVG(socPct) AS socPct,
               MIN(socPct) AS minSocPct,
               MAX(socPct) AS maxSocPct,
               AVG(cellDeltaV) AS cellDeltaV,
               MIN(cellDeltaV) AS minCellDeltaV,
               MAX(cellDeltaV) AS maxCellDeltaV,
               AVG(mosTemperatureC) AS mosTemperatureC,
               MIN(mosTemperatureC) AS minMosTemperatureC,
               MAX(mosTemperatureC) AS maxMosTemperatureC,
               AVG(ambientTemperatureC) AS ambientTemperatureC,
               MIN(ambientTemperatureC) AS minAmbientTemperatureC,
               MAX(ambientTemperatureC) AS maxAmbientTemperatureC
        FROM telemetry_samples
        WHERE timestampEpochMs BETWEEN :startEpochMs AND :endEpochMs
        GROUP BY ((timestampEpochMs - :startEpochMs) / :bucketMs)
        ORDER BY timestampEpochMs
    """) fun observeChartPoints(startEpochMs: Long, endEpochMs: Long, bucketMs: Long): Flow<List<TelemetryChartPoint>>
    @Query("SELECT * FROM power_sessions WHERE endEpochMs >= :startEpochMs AND startEpochMs <= :endEpochMs ORDER BY startEpochMs DESC LIMIT :limit") fun observePowerSessionsBetween(startEpochMs: Long, endEpochMs: Long, limit: Int): Flow<List<PowerSessionEntity>>
    @Query("""
        SELECT connection_sessions.id AS id,
               connection_sessions.startedAtEpochMs AS startedAtEpochMs,
               connection_sessions.endedAtEpochMs AS endedAtEpochMs,
               connection_sessions.endReason AS endReason,
               MIN(telemetry_samples.timestampEpochMs) AS firstSampleAtEpochMs,
               MAX(telemetry_samples.timestampEpochMs) AS lastSampleAtEpochMs,
               COUNT(telemetry_samples.id) AS sampleCount,
               (SELECT first_sample.socPct FROM telemetry_samples AS first_sample
                WHERE first_sample.connectionSessionId = connection_sessions.id
                ORDER BY first_sample.timestampEpochMs ASC LIMIT 1) AS startSocPct,
               (SELECT last_sample.socPct FROM telemetry_samples AS last_sample
                WHERE last_sample.connectionSessionId = connection_sessions.id
                ORDER BY last_sample.timestampEpochMs DESC LIMIT 1) AS endSocPct,
               AVG(ABS(telemetry_samples.powerW)) AS averageAbsolutePowerW,
               MAX(ABS(telemetry_samples.powerW)) AS peakAbsolutePowerW,
               MIN(telemetry_samples.voltageV) AS minimumVoltageV,
               MAX(MAX(telemetry_samples.mosTemperatureC, telemetry_samples.ambientTemperatureC)) AS maximumTemperatureC
        FROM connection_sessions
        INNER JOIN telemetry_samples ON telemetry_samples.connectionSessionId = connection_sessions.id
        WHERE connection_sessions.startedAtEpochMs <= :endEpochMs
          AND COALESCE(connection_sessions.endedAtEpochMs, :endEpochMs) >= :startEpochMs
        GROUP BY connection_sessions.id
        HAVING SUM(CASE WHEN telemetry_samples.recordingId IS NOT NULL THEN 1 ELSE 0 END)=0
        ORDER BY connection_sessions.startedAtEpochMs DESC
        LIMIT :limit
    """) fun observeRecordingsBetween(startEpochMs: Long, endEpochMs: Long, limit: Int): Flow<List<RecordingHistoryItem>>
    @Query("SELECT * FROM daily_summaries WHERE dayStartEpochMs BETWEEN :startEpochMs AND :endEpochMs ORDER BY dayStartEpochMs DESC") fun observeDailySummariesBetween(startEpochMs: Long, endEpochMs: Long): Flow<List<DailySummaryEntity>>
    @Query("SELECT * FROM telemetry_samples ORDER BY timestampEpochMs DESC LIMIT 1") suspend fun latestSample(): TelemetrySampleEntity?
    @Query("SELECT * FROM telemetry_samples WHERE connectionSessionId=:sessionId ORDER BY timestampEpochMs DESC,id DESC LIMIT 1") suspend fun latestSampleForSession(sessionId: Long): TelemetrySampleEntity?
    @Query("SELECT * FROM solar_daily_rollups ORDER BY localDate") fun observeSolarDays(): Flow<List<SolarDailyRollupEntity>>
    @Query("SELECT * FROM solar_daily_rollups WHERE localDate BETWEEN :startDate AND :endDate ORDER BY localDate DESC") fun observeSolarDaysBetween(startDate: String, endDate: String): Flow<List<SolarDailyRollupEntity>>
    @Query("SELECT * FROM solar_daily_rollups WHERE localDate=:localDate LIMIT 1") suspend fun solarDay(localDate: String): SolarDailyRollupEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSolarDay(day: SolarDailyRollupEntity)
    @Query("DELETE FROM solar_daily_rollups") suspend fun clearSolarDays()
    @Query("SELECT t.* FROM telemetry_samples t JOIN connection_sessions s ON s.id=t.connectionSessionId WHERE s.origin='RECORDED' AND t.timestampEpochMs<=:atMs ORDER BY t.timestampEpochMs DESC,t.id DESC LIMIT 1") suspend fun recordedSampleAtOrBefore(atMs: Long): TelemetrySampleEntity?
    @Query("SELECT t.* FROM telemetry_samples t JOIN connection_sessions s ON s.id=t.connectionSessionId WHERE s.origin='RECORDED' AND (t.timestampEpochMs>:afterMs OR (t.timestampEpochMs=:afterMs AND t.id>:afterId)) ORDER BY t.timestampEpochMs,t.id LIMIT :limit") suspend fun recordedSamplesAfter(afterMs: Long, afterId: Long, limit: Int): List<TelemetrySampleEntity>
    @Query("SELECT t.* FROM telemetry_samples t JOIN connection_sessions s ON s.id=t.connectionSessionId WHERE s.origin='RECORDED' AND t.timestampEpochMs BETWEEN :startMs AND :endMs ORDER BY t.timestampEpochMs,t.id") suspend fun recordedSamplesBetween(startMs: Long, endMs: Long): List<TelemetrySampleEntity>
    @Query("SELECT * FROM telemetry_samples WHERE timestampEpochMs <= :asOfEpochMs AND socPct BETWEEN :minimumSoc AND 100 ORDER BY timestampEpochMs DESC, id DESC LIMIT 1") suspend fun latestNearFullSample(minimumSoc: Int, asOfEpochMs: Long): TelemetrySampleEntity?
    @Query("SELECT * FROM telemetry_samples WHERE timestampEpochMs <= :asOfEpochMs ORDER BY timestampEpochMs DESC, id DESC LIMIT 1") suspend fun latestSampleAtOrBefore(asOfEpochMs: Long): TelemetrySampleEntity?
    @Query("SELECT * FROM telemetry_samples WHERE timestampEpochMs < :beforeTimestamp OR (timestampEpochMs = :beforeTimestamp AND id < :beforeId) ORDER BY timestampEpochMs DESC, id DESC LIMIT :limit") suspend fun samplesBefore(beforeTimestamp: Long, beforeId: Long, limit: Int): List<TelemetrySampleEntity>
    @Query("SELECT * FROM telemetry_samples WHERE timestampEpochMs BETWEEN :startEpochMs AND :endEpochMs AND socPct <= :targetSoc ORDER BY timestampEpochMs, id LIMIT 1") suspend fun firstSampleAtOrBelowSoc(startEpochMs: Long, endEpochMs: Long, targetSoc: Int): TelemetrySampleEntity?
    @Query("SELECT * FROM telemetry_samples WHERE timestampEpochMs <= :endEpochMs AND (timestampEpochMs > :afterTimestamp OR (timestampEpochMs = :afterTimestamp AND id > :afterId)) ORDER BY timestampEpochMs, id LIMIT :limit") suspend fun usageSamplesPage(afterTimestamp: Long, afterId: Long, endEpochMs: Long, limit: Int): List<TelemetrySampleEntity>
    @Query("SELECT * FROM telemetry_samples ORDER BY timestampEpochMs") suspend fun allSamplesAscending(): List<TelemetrySampleEntity>
    @Query("SELECT * FROM telemetry_samples WHERE timestampEpochMs >= :startEpochMs ORDER BY timestampEpochMs") suspend fun samplesSince(startEpochMs: Long): List<TelemetrySampleEntity>
    @Query("SELECT * FROM telemetry_samples WHERE timestampEpochMs BETWEEN :startEpochMs AND :endEpochMs ORDER BY timestampEpochMs") suspend fun samplesBetween(startEpochMs: Long, endEpochMs: Long): List<TelemetrySampleEntity>
    @Query("DELETE FROM power_sessions") suspend fun clearPowerSessions()
    @Query("DELETE FROM power_sessions WHERE startEpochMs >= :startEpochMs") suspend fun clearPowerSessionsSince(startEpochMs: Long)
    @Insert suspend fun insertPowerSessions(sessions: List<PowerSessionEntity>)
    @Query("DELETE FROM daily_summaries") suspend fun clearDailySummaries()
    @Query("DELETE FROM daily_summaries WHERE dayStartEpochMs >= :startEpochMs") suspend fun clearDailySummariesSince(startEpochMs: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDailySummaries(summaries: List<DailySummaryEntity>)
    @Query("DELETE FROM telemetry_samples") suspend fun clearTelemetrySamples()
    @Query("DELETE FROM telemetry_samples WHERE timestampEpochMs < :startEpochMs") suspend fun deleteTelemetrySamplesBefore(startEpochMs: Long)
    @Query("DELETE FROM connection_sessions WHERE endedAtEpochMs IS NOT NULL") suspend fun clearClosedConnectionSessions()
    @Query("DELETE FROM connection_sessions WHERE endedAtEpochMs IS NOT NULL AND NOT EXISTS (SELECT 1 FROM telemetry_samples WHERE telemetry_samples.connectionSessionId = connection_sessions.id)") suspend fun deleteClosedSessionsWithoutSamples()
    @Query("DELETE FROM flow_recordings WHERE endedAtMs IS NOT NULL AND startedAtMs < :startEpochMs AND NOT EXISTS (SELECT 1 FROM telemetry_samples WHERE telemetry_samples.recordingId = flow_recordings.id AND telemetry_samples.timestampEpochMs >= :startEpochMs)") suspend fun deleteClosedFlowRecordingsBefore(startEpochMs: Long)
    @Insert suspend fun insertCapacityTest(test: CapacityTestEntity): Long
    @Query("UPDATE capacity_tests SET state=:state, pausedAtMs=:paused, endedAtMs=:ended WHERE id=:id") suspend fun updateCapacityTest(id: Long, state: String, paused: Long?, ended: Long?)
    @Query("UPDATE capacity_tests SET state='RUNNING', pausedAtMs=NULL, segment=segment+1 WHERE id=:id AND state='PAUSED'") suspend fun resumeCapacityTest(id: Long)
    @Insert suspend fun insertCapacitySample(sample: CapacityTestSampleEntity)
    @Insert suspend fun insertCapacitySamples(samples: List<CapacityTestSampleEntity>)
    @Query("SELECT * FROM capacity_tests WHERE state IN ('RUNNING','PAUSED') LIMIT 1") suspend fun activeCapacityTest(): CapacityTestEntity?
    @Query("SELECT * FROM capacity_tests WHERE startedAtMs=:startedAtMs LIMIT 1") suspend fun capacityTestAt(startedAtMs: Long): CapacityTestEntity?
    @Query("SELECT * FROM capacity_tests ORDER BY startedAtMs DESC") fun observeCapacityTests(): Flow<List<CapacityTestEntity>>
    @Query("SELECT * FROM capacity_test_samples WHERE testId=:testId ORDER BY timestampMs") suspend fun capacitySamples(testId: Long): List<CapacityTestSampleEntity>
    @Transaction suspend fun insertCapacitySampleIfActive(sample: CapacityTestSampleEntity): Boolean {
        val active = activeCapacityTest()
        if (active == null || active.id != sample.testId || active.segment != sample.segment || active.state != "RUNNING") return false
        insertCapacitySample(sample)
        return true
    }
    @Transaction suspend fun clearTelemetryHistory() {
        clearSolarDays()
        clearFlowRecordings()
        clearPowerSessions()
        clearDailySummaries()
        clearTelemetrySamples()
        clearClosedConnectionSessions()
    }
}

@Database(entities = [ConnectionSessionEntity::class, TelemetrySampleEntity::class, PowerSessionEntity::class, DailySummaryEntity::class, CapacityTestEntity::class, CapacityTestSampleEntity::class, AlertEventEntity::class, SolarDailyRollupEntity::class, FlowRecordingEntity::class, MonitoringPeriodEntity::class], version = 8, exportSchema = true)
abstract class TelemetryDatabase : RoomDatabase() {
    abstract fun telemetryDao(): TelemetryDao
    companion object {
        @Volatile private var instance: TelemetryDatabase? = null
        fun get(context: Context): TelemetryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TelemetryDatabase::class.java, "gentai-telemetry.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build().also { instance = it }
        }
        private val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS power_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mode TEXT NOT NULL, startEpochMs INTEGER NOT NULL, endEpochMs INTEGER NOT NULL, startSocPct INTEGER, endSocPct INTEGER, chargedAh REAL NOT NULL, dischargedAh REAL NOT NULL, chargedWh REAL NOT NULL, dischargedWh REAL NOT NULL, averagePowerW REAL NOT NULL, peakAbsolutePowerW REAL NOT NULL, sampleCount INTEGER NOT NULL, coveragePct REAL NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_power_sessions_startEpochMs ON power_sessions (startEpochMs)")
            db.execSQL("CREATE TABLE IF NOT EXISTS daily_summaries (dayStartEpochMs INTEGER NOT NULL, chargedAh REAL NOT NULL, dischargedAh REAL NOT NULL, chargedWh REAL NOT NULL, dischargedWh REAL NOT NULL, observedMs INTEGER NOT NULL, missingMs INTEGER NOT NULL, coveragePct REAL NOT NULL, sampleCount INTEGER NOT NULL, PRIMARY KEY(dayStartEpochMs))")
        } }
        private val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS capacity_tests (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, state TEXT NOT NULL, startedAtMs INTEGER NOT NULL, pausedAtMs INTEGER, endedAtMs INTEGER)"); db.execSQL("CREATE TABLE IF NOT EXISTS capacity_test_samples (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, testId INTEGER NOT NULL, timestampMs INTEGER NOT NULL, voltageV REAL NOT NULL, currentA REAL NOT NULL, socPct INTEGER NOT NULL, FOREIGN KEY(testId) REFERENCES capacity_tests(id) ON DELETE CASCADE)"); db.execSQL("CREATE INDEX IF NOT EXISTS index_capacity_test_samples_testId ON capacity_test_samples(testId)") } }
        private val MIGRATION_3_4 = object : Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE capacity_tests ADD COLUMN segment INTEGER NOT NULL DEFAULT 0"); db.execSQL("ALTER TABLE capacity_test_samples ADD COLUMN segment INTEGER NOT NULL DEFAULT 0") } }
        private val MIGRATION_4_5 = object : Migration(4, 5) { override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS alert_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestampEpochMs INTEGER NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, message TEXT NOT NULL, socPct INTEGER)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_alert_events_timestampEpochMs ON alert_events (timestampEpochMs)")
        } }
        internal val MIGRATION_5_6 = object : Migration(5, 6) { override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE connection_sessions ADD COLUMN origin TEXT NOT NULL DEFAULT 'RECORDED'")
            db.execSQL("UPDATE connection_sessions SET origin='IMPORTED' WHERE endReason='Imported capacity test'")
            db.execSQL("CREATE TABLE IF NOT EXISTS solar_daily_rollups (localDate TEXT NOT NULL PRIMARY KEY, chargedWh REAL NOT NULL, usedWh REAL NOT NULL, observedMs INTEGER NOT NULL, chargingMs INTEGER NOT NULL, dischargingMs INTEGER NOT NULL)")
        } }
        internal val MIGRATION_6_7 = object : Migration(6, 7) { override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS flow_recordings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mode TEXT NOT NULL, startedAtMs INTEGER NOT NULL, endedAtMs INTEGER, endReason TEXT, chargedAh REAL NOT NULL DEFAULT 0, dischargedAh REAL NOT NULL DEFAULT 0, chargedWh REAL NOT NULL DEFAULT 0, dischargedWh REAL NOT NULL DEFAULT 0, observedMs INTEGER NOT NULL DEFAULT 0, missingMs INTEGER NOT NULL DEFAULT 0, sampleCount INTEGER NOT NULL DEFAULT 0, averagePowerW REAL NOT NULL DEFAULT 0, peakPowerW REAL NOT NULL DEFAULT 0, startSocPct INTEGER, endSocPct INTEGER)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_flow_recordings_startedAtMs ON flow_recordings (startedAtMs)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_flow_recordings_mode ON flow_recordings (mode)")
            db.execSQL("ALTER TABLE telemetry_samples ADD COLUMN recordingId INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_telemetry_samples_recordingId ON telemetry_samples (recordingId)")
        } }
        internal val MIGRATION_7_8 = object : Migration(7, 8) { override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS monitoring_periods (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, startedAtMs INTEGER NOT NULL, endedAtMs INTEGER, endReason TEXT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_monitoring_periods_startedAtMs ON monitoring_periods (startedAtMs)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_monitoring_periods_endedAtMs ON monitoring_periods (endedAtMs)")
        } }
    }
}

class GentaiTelemetryRepository(private val database: TelemetryDatabase) {
    val samples: Flow<List<TelemetrySampleEntity>> = database.telemetryDao().observeSamples()
    fun recentSamples(limit: Int = 240): Flow<List<TelemetrySampleEntity>> = database.telemetryDao().observeRecentSamples(limit)
    fun recentPowerSessions(limit: Int = 50): Flow<List<PowerSessionEntity>> = database.telemetryDao().observeRecentPowerSessions(limit)
    fun recentDailySummaries(limit: Int = 30): Flow<List<DailySummaryEntity>> = database.telemetryDao().observeRecentDailySummaries(limit)
    fun chartPoints(startEpochMs: Long, endEpochMs: Long, bucketMs: Long): Flow<List<TelemetryChartPoint>> = database.telemetryDao().observeChartPoints(startEpochMs, endEpochMs, bucketMs)
    fun powerSessionsBetween(startEpochMs: Long, endEpochMs: Long, limit: Int = 200): Flow<List<PowerSessionEntity>> = database.telemetryDao().observePowerSessionsBetween(startEpochMs, endEpochMs, limit)
    fun recordingsBetween(startEpochMs: Long, endEpochMs: Long, limit: Int = 200): Flow<List<RecordingHistoryItem>> = database.telemetryDao().observeRecordingsBetween(startEpochMs, endEpochMs, limit)
    fun flowRecordingsBetween(startEpochMs: Long, endEpochMs: Long, limit: Int = 1000): Flow<List<FlowRecordingEntity>> = database.telemetryDao().observeFlowRecordingsBetween(startEpochMs, endEpochMs, limit)
    fun flowRecordingCount(startEpochMs: Long, endEpochMs: Long): Flow<Int> = database.telemetryDao().observeFlowRecordingCount(startEpochMs, endEpochMs)
    fun dayFlowRecordingCount(startEpochMs: Long, endEpochMs: Long, mode: String?): Flow<Int> = database.telemetryDao().observeDayFlowRecordingCount(startEpochMs, endEpochMs, mode)
    fun dayFlowRecordings(startEpochMs: Long, endEpochMs: Long, mode: String?, limit: Int): Flow<List<FlowRecordingEntity>> = database.telemetryDao().observeDayFlowRecordings(startEpochMs, endEpochMs, mode, limit)
    fun flowRecordingById(id: Long): Flow<FlowRecordingEntity?> = database.telemetryDao().observeFlowRecording(id)
    fun recordingChart(recordingId: Long, startEpochMs: Long, endEpochMs: Long, bucketMs: Long): Flow<List<RecordingChartPoint>> =
        database.telemetryDao().observeRecordingChart(recordingId, startEpochMs, endEpochMs, bucketMs)
    fun monitoringPeriods(limit: Int = 100): Flow<List<MonitoringPeriodEntity>> = database.telemetryDao().observeMonitoringPeriods(limit)
    suspend fun flowRecordingSamples(recordingId: Long): List<TelemetrySampleEntity> = database.telemetryDao().flowRecordingSamples(recordingId)
    fun observeFlowRecordingSamples(recordingId: Long): Flow<List<TelemetrySampleEntity>> = database.telemetryDao().observeFlowRecordingSamples(recordingId)
    suspend fun assignSampleToFlowRecording(sampleId: Long, recordingId: Long) = DATABASE_WRITE_MUTEX.withLock {
        database.telemetryDao().assignSampleToRecording(sampleId, recordingId)
    }
    suspend fun markSampleAsGap(sampleId: Long) = DATABASE_WRITE_MUTEX.withLock {
        val dao = database.telemetryDao()
        val sample = dao.sample(sampleId)
        val previous = dao.sampleBefore(sampleId)
        if (sample != null && previous != null) dao.markSampleGap(sampleId, (sample.timestampEpochMs - previous.timestampEpochMs).coerceAtLeast(1L))
    }
    suspend fun beginFlowRecording(mode: FlowRecordingMode, now: Long = System.currentTimeMillis()): Long = DATABASE_WRITE_MUTEX.withLock {
        database.telemetryDao().insertFlowRecording(FlowRecordingEntity(mode = mode.name, startedAtMs = now))
    }
    suspend fun endFlowRecording(id: Long, reason: String = "Stopped", now: Long = System.currentTimeMillis()) = DATABASE_WRITE_MUTEX.withLock {
        val dao = database.telemetryDao()
        val existing = dao.flowRecording(id) ?: return@withLock
        if (existing.endedAtMs != null) return@withLock
        val samples = dao.flowRecordingSamples(id)
        if (samples.size < 2) {
            dao.deleteFlowRecording(id)
            return@withLock
        }
        val totals = FlowRecordingAnalytics.summarize(samples.map { it.toFlowSample() })
        dao.closeFlowRecording(id, now, reason, totals.chargedAh, totals.dischargedAh, totals.chargedWh, totals.dischargedWh, totals.observedMs, totals.missingMs, totals.sampleCount, totals.averagePowerW, totals.peakPowerW, totals.startSocPct, totals.endSocPct)
    }
    suspend fun refreshFlowRecording(id: Long) = DATABASE_WRITE_MUTEX.withLock {
        val dao = database.telemetryDao()
        if (dao.flowRecording(id)?.endedAtMs != null) return@withLock
        val totals = FlowRecordingAnalytics.summarize(dao.flowRecordingSamples(id).map { it.toFlowSample() })
        dao.updateFlowRecordingSummary(id, totals.chargedAh, totals.dischargedAh, totals.chargedWh, totals.dischargedWh, totals.observedMs, totals.missingMs, totals.sampleCount, totals.averagePowerW, totals.peakPowerW, totals.startSocPct, totals.endSocPct)
    }
    suspend fun recoverOpenFlowRecordings() = DATABASE_WRITE_MUTEX.withLock {
        val dao = database.telemetryDao()
        dao.openFlowRecordings().forEach { recording ->
            val samples = dao.flowRecordingSamples(recording.id)
            if (samples.size < 2) {
                dao.deleteFlowRecording(recording.id)
            } else {
                val totals = FlowRecordingAnalytics.summarize(samples.map { it.toFlowSample() })
                dao.closeFlowRecording(recording.id, samples.last().timestampEpochMs, "Previous process interrupted", totals.chargedAh, totals.dischargedAh, totals.chargedWh, totals.dischargedWh, totals.observedMs, totals.missingMs, totals.sampleCount, totals.averagePowerW, totals.peakPowerW, totals.startSocPct, totals.endSocPct)
            }
        }
    }
    fun dailySummariesBetween(startEpochMs: Long, endEpochMs: Long): Flow<List<DailySummaryEntity>> = database.telemetryDao().observeDailySummariesBetween(startEpochMs, endEpochMs)
    fun solarDaysBetween(startDate: String, endDate: String): Flow<List<SolarDailyRollupEntity>> = database.telemetryDao().observeSolarDaysBetween(startDate, endDate)
    suspend fun analyticsSamplesBetween(startEpochMs: Long, endEpochMs: Long): List<AnalyticsSample> = database.telemetryDao().samplesBetween(startEpochMs, endEpochMs).map {
        val probeMaximum = it.probeTemperaturesC.split(',').mapNotNull(String::toIntOrNull).maxOrNull()
        AnalyticsSample(it.timestampEpochMs, it.voltageV, it.currentA, it.socPct, listOfNotNull(probeMaximum, it.ambientTemperatureC, it.mosTemperatureC).maxOrNull(), it.cellDeltaV, it.gapFromPreviousMs != null)
    }
    suspend fun recordedAnalyticsSamplesBetween(startEpochMs: Long, endEpochMs: Long): List<AnalyticsSample> = database.telemetryDao().recordedSamplesBetween(startEpochMs, endEpochMs).map {
        val probeMaximum = it.probeTemperaturesC.split(',').mapNotNull(String::toIntOrNull).maxOrNull()
        AnalyticsSample(it.timestampEpochMs, it.voltageV, it.currentA, it.socPct, listOfNotNull(probeMaximum, it.ambientTemperatureC, it.mosTemperatureC).maxOrNull(), it.cellDeltaV, it.gapFromPreviousMs != null)
    }
    suspend fun batteryUsageCycle(asOfEpochMs: Long, maxChartPoints: Int = 360): BatteryUsageCycle? {
        val dao = database.telemetryDao()
        val latestNearFull = dao.latestNearFullSample(BatteryUsageAccumulator.NEAR_FULL_SOC, asOfEpochMs) ?: return null
        val peakTracker = NearFullPeakTracker(latestNearFull.toBatteryUsageSample())
        var cursor = latestNearFull
        var searching = true
        while (searching) {
            val page = dao.samplesBefore(cursor.timestampEpochMs, cursor.id, USAGE_PAGE_SIZE)
            if (page.isEmpty()) break
            for (sample in page) {
                if (!peakTracker.addOlder(sample.toBatteryUsageSample())) {
                    searching = false
                    break
                }
                cursor = sample
            }
            if (page.size < USAGE_PAGE_SIZE) break
        }

        val peak = peakTracker.peak
        val completedAt = dao.firstSampleAtOrBelowSoc(peak.timestampMs, asOfEpochMs, BatteryUsageAccumulator.TARGET_SOC)
        val end = completedAt ?: dao.latestSampleAtOrBefore(asOfEpochMs) ?: return null
        if (end.timestampEpochMs < peak.timestampMs) return null
        val accumulator = BatteryUsageAccumulator(peak.timestampMs, end.timestampEpochMs, maxChartPoints)
        accumulator.add(peak)
        var afterTimestamp = peak.timestampMs
        var afterId = peak.id
        while (true) {
            val page = dao.usageSamplesPage(afterTimestamp, afterId, end.timestampEpochMs, USAGE_PAGE_SIZE)
            if (page.isEmpty()) break
            page.forEach { accumulator.add(it.toBatteryUsageSample()) }
            val last = page.last()
            afterTimestamp = last.timestampEpochMs
            afterId = last.id
            if (page.size < USAGE_PAGE_SIZE) break
        }
        return accumulator.finish()
    }
    private var sessionId: Long? = null

    suspend fun beginSession(now: Long = System.currentTimeMillis()): Long = DATABASE_WRITE_MUTEX.withLock {
        database.telemetryDao().closeOpenSessions(now, "Previous process interrupted")
        database.telemetryDao().insertSession(ConnectionSessionEntity(startedAtEpochMs = now)).also { sessionId = it }
    }
    suspend fun beginMonitoringPeriod(now: Long = System.currentTimeMillis()): Long = DATABASE_WRITE_MUTEX.withLock {
        database.telemetryDao().openMonitoringPeriod()?.id
            ?: database.telemetryDao().insertMonitoringPeriod(MonitoringPeriodEntity(startedAtMs = now))
    }
    suspend fun endMonitoringPeriod(reason: String, now: Long = System.currentTimeMillis()) = DATABASE_WRITE_MUTEX.withLock {
        database.telemetryDao().closeOpenMonitoringPeriods(now, reason)
    }
    suspend fun endSession(reason: String, now: Long = System.currentTimeMillis()) = DATABASE_WRITE_MUTEX.withLock { sessionId?.let { database.telemetryDao().closeSession(it, now, reason) }; sessionId = null }
    suspend fun record(info: BatteryInfo, cellsV: List<Double> = emptyList(), status: RunningStatus? = null, now: Long = System.currentTimeMillis(), flowRecordingId: Long? = null): Long {
        val session = checkNotNull(sessionId) { "Cannot save telemetry without an active connection session" }
        return recordForSession(session, info, cellsV, status, now, flowRecordingId = flowRecordingId)
    }
    suspend fun recordForSession(session: Long, info: BatteryInfo, cellsV: List<Double> = emptyList(), status: RunningStatus? = null, now: Long = System.currentTimeMillis(), forceGap: Boolean = false, energySettings: EnergySettings? = null, flowRecordingId: Long? = null): Long = DATABASE_WRITE_MUTEX.withLock {
        var insertedId = 0L
        database.withTransaction {
        val dao = database.telemetryDao()
        val openFlowRecordingId = flowRecordingId?.takeIf { dao.flowRecording(it)?.endedAtMs == null }
        val previousInSession = dao.latestSampleForSession(session)
        val previous = previousInSession ?: dao.latestSample()
        val elapsed = previous?.let { now - it.timestampEpochMs }
        val gap = elapsed?.takeIf { it > 0 && (it > GAP_THRESHOLD_MS || forceGap || previous.connectionSessionId != session) }
        val min = cellsV.minOrNull(); val max = cellsV.maxOrNull()
        val sample = TelemetrySampleEntity(
            timestampEpochMs = now, connectionSessionId = session, voltageV = info.voltageV, currentA = info.currentA, powerW = info.voltageV * info.currentA,
            socPct = info.socPct, sohPct = info.sohPct, remainingAh = info.remainingAh, fullAh = info.fullAh, cycleCount = info.cycles,
            cellVoltagesV = cellsV.joinToString(","), cellMinV = min, cellMaxV = max, cellDeltaV = if (min == null) null else max!! - min,
            probeTemperaturesC = info.temperaturesC.probes.joinToString(","), ambientTemperatureC = info.temperaturesC.ambient, mosTemperatureC = info.temperaturesC.mos,
            protectionBitfield = status?.bitfield, balanceMask = status?.balanceMask, disconnectedCellMask = status?.disconnectedCellMask, gapFromPreviousMs = gap, recordingId = openFlowRecordingId,
        )
        insertedId = dao.insertSample(sample)
        if (energySettings != null && previousInSession != null) {
            SolarEnergyAnalytics.dailyParts(previousInSession.toSolarReading(), sample.toSolarReading(), energySettings.trackingStartMs, energySettings.zone).forEach { part ->
                val date = Instant.ofEpochMilli(part.startMs).atZone(energySettings.zone).toLocalDate().toString()
                val old = dao.solarDay(date)
                val amount = (old?.let { SolarEnergyAmount(it.chargedWh, it.usedWh, it.observedMs, it.chargingMs, it.dischargingMs) } ?: SolarEnergyAmount()) + part.amount
                dao.upsertSolarDay(SolarDailyRollupEntity(date, amount.chargedWh, amount.usedWh, amount.observedMs, amount.chargingMs, amount.dischargingMs))
            }
        }
        }
        if (lastAnalyticsRebuildAtMs == 0L || now - lastAnalyticsRebuildAtMs >= ANALYTICS_REFRESH_MS) {
            rebuildAnalyticsLocked(now)
            lastAnalyticsRebuildAtMs = now
        }
        insertedId
    }
    suspend fun rebuildAnalytics(now: Long = System.currentTimeMillis()) = DATABASE_WRITE_MUTEX.withLock { rebuildAnalyticsLocked(now) }
    private suspend fun rebuildAnalyticsLocked(now: Long, rebuildAll: Boolean = false) {
        val dao = database.telemetryDao()
        val dayStart = Math.floorDiv(now, DAY_MS) * DAY_MS
        val source = if (rebuildAll) dao.allSamplesAscending() else dao.samplesSince(dayStart)
        val raw = source.map { AnalyticsSample(it.timestampEpochMs, it.voltageV, it.currentA, it.socPct, maxOf(it.ambientTemperatureC, it.mosTemperatureC), it.cellDeltaV, it.gapFromPreviousMs != null) }
        if (rebuildAll) dao.clearPowerSessions() else dao.clearPowerSessionsSince(dayStart)
        dao.insertPowerSessions(TelemetryAnalytics.sessions(raw).map { session -> PowerSessionEntity(
            mode = session.mode.name, startEpochMs = session.startMs, endEpochMs = session.endMs, startSocPct = session.startSocPct, endSocPct = session.endSocPct,
            chargedAh = session.totals.chargedAh, dischargedAh = session.totals.dischargedAh, chargedWh = session.totals.chargedWh, dischargedWh = session.totals.dischargedWh,
            averagePowerW = session.averageAbsolutePowerW, peakAbsolutePowerW = session.peakAbsolutePowerW, sampleCount = session.sampleCount, coveragePct = session.totals.coveragePct,
        ) })
        if (rebuildAll) dao.clearDailySummaries() else dao.clearDailySummariesSince(dayStart)
        dao.insertDailySummaries(TelemetryAnalytics.daily(raw).map { day -> DailySummaryEntity(day.dayStartMs, day.totals.chargedAh, day.totals.dischargedAh, day.totals.chargedWh, day.totals.dischargedWh, day.totals.observedMs, day.totals.missingMs, day.totals.coveragePct, day.sampleCount) })
    }
    suspend fun deleteTelemetryHistory(preferences: com.warwar13.gentai.analytics.EnergyPreferences) = DATABASE_WRITE_MUTEX.withLock {
        // Persist the new baseline before deleting rows. This also prevents the bundled
        // wall-era CSV import from restoring telemetry on the next app launch.
        preferences.resetAfterHistoryDeletion()
        database.telemetryDao().clearTelemetryHistory()
        lastAnalyticsRebuildAtMs = 0L
    }
    suspend fun deleteTelemetryBeforeKeepingToday(
        preferences: com.warwar13.gentai.analytics.EnergyPreferences,
        nowMs: Long = System.currentTimeMillis(),
    ) = DATABASE_WRITE_MUTEX.withLock {
        val zone = preferences.settings.value.zone
        val todayStartMs = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        preferences.resetAfterHistoryDeletion(todayStartMs)
        database.withTransaction {
            val dao = database.telemetryDao()
            dao.deleteTelemetrySamplesBefore(todayStartMs)
            dao.deleteClosedFlowRecordingsBefore(todayStartMs)
            dao.deleteClosedSessionsWithoutSamples()
            dao.clearPowerSessions()
            dao.clearDailySummaries()
            dao.clearSolarDays()
        }
        rebuildFlowRecordingSummariesLocked()
        rebuildAnalyticsLocked(nowMs, rebuildAll = true)
        rebuildSolarRollupsLocked(preferences.settings.value)
        lastAnalyticsRebuildAtMs = nowMs
    }
    suspend fun rebuildSolarRollups(settings: EnergySettings) = DATABASE_WRITE_MUTEX.withLock {
        rebuildSolarRollupsLocked(settings)
    }
    private suspend fun rebuildSolarRollupsLocked(settings: EnergySettings) {
        val dao = database.telemetryDao()
        val daily = linkedMapOf<String, SolarEnergyAmount>()
        var previous = dao.recordedSampleAtOrBefore(settings.trackingStartMs)
        var afterMs = previous?.timestampEpochMs ?: settings.trackingStartMs - 1
        var afterId = previous?.id ?: 0L
        while (true) {
            val page = dao.recordedSamplesAfter(afterMs, afterId, USAGE_PAGE_SIZE)
            if (page.isEmpty()) break
            for (sample in page) {
                if (previous != null) {
                    SolarEnergyAnalytics.dailyParts(previous.toSolarReading(), sample.toSolarReading(), settings.trackingStartMs, settings.zone).forEach { part ->
                        val date = Instant.ofEpochMilli(part.startMs).atZone(settings.zone).toLocalDate().toString()
                        daily[date] = (daily[date] ?: SolarEnergyAmount()) + part.amount
                    }
                }
                previous = sample
            }
            val last = page.last()
            afterMs = last.timestampEpochMs
            afterId = last.id
            if (page.size < USAGE_PAGE_SIZE) break
        }
        database.withTransaction {
            dao.clearSolarDays()
            daily.forEach { (date, amount) -> dao.upsertSolarDay(SolarDailyRollupEntity(date, amount.chargedWh, amount.usedWh, amount.observedMs, amount.chargingMs, amount.dischargingMs)) }
        }
    }
    private suspend fun rebuildFlowRecordingSummariesLocked() {
        val dao = database.telemetryDao()
        database.withTransaction {
            dao.allFlowRecordings().forEach { recording ->
                val totals = FlowRecordingAnalytics.summarize(dao.flowRecordingSamples(recording.id).map { it.toFlowSample() })
                dao.updateFlowRecordingSummary(recording.id, totals.chargedAh, totals.dischargedAh, totals.chargedWh, totals.dischargedWh, totals.observedMs, totals.missingMs, totals.sampleCount, totals.averagePowerW, totals.peakPowerW, totals.startSocPct, totals.endSocPct)
            }
        }
    }
    companion object {
        private val DATABASE_WRITE_MUTEX = Mutex()
        private var lastAnalyticsRebuildAtMs = 0L
        const val GAP_THRESHOLD_MS = 30_000L
        const val ANALYTICS_REFRESH_MS = 300_000L
        const val DAY_MS = 86_400_000L
        private const val USAGE_PAGE_SIZE = 1_000
    }
}

private fun TelemetrySampleEntity.toSolarReading() = SolarReading(timestampEpochMs, powerW, connectionSessionId, gapFromPreviousMs != null)

private fun TelemetrySampleEntity.toFlowSample() = FlowSample(timestampEpochMs, voltageV, currentA, powerW, socPct, gapFromPreviousMs)

private fun TelemetrySampleEntity.toBatteryUsageSample() = BatteryUsageSample(
    id = id,
    timestampMs = timestampEpochMs,
    voltageV = voltageV,
    currentA = currentA,
    socPct = socPct,
    remainingAh = remainingAh,
    fullAh = fullAh,
    gapFromPreviousMs = gapFromPreviousMs,
)

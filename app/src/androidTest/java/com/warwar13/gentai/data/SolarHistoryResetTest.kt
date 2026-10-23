package com.warwar13.gentai.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.warwar13.gentai.analytics.EnergyPreferences
import com.warwar13.gentai.capacity.CapacityTestRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringReader

@RunWith(AndroidJUnit4::class)
class SolarHistoryResetTest {
    @Test fun resetClearsTelemetryButKeepsCapacityAndAlertsAndBlocksBundledImport() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TelemetryDatabase::class.java).build()
        try {
            val dao = db.telemetryDao()
            // Use the test APK's preferences so this test cannot alter a real app baseline.
            val preferences = EnergyPreferences.isolatedForTest(InstrumentationRegistry.getInstrumentation().context)
            val session = dao.insertSession(ConnectionSessionEntity(startedAtEpochMs = 1_000, endedAtEpochMs = 2_000))
            dao.insertSample(TelemetrySampleEntity(
                timestampEpochMs = 1_500, connectionSessionId = session,
                voltageV = 52.0, currentA = -2.0, powerW = -104.0,
                socPct = 80, sohPct = 100, remainingAh = 80.0, fullAh = 100.0,
                cycleCount = 1, cellVoltagesV = "", cellMinV = null, cellMaxV = null,
                cellDeltaV = null, probeTemperaturesC = "", ambientTemperatureC = 25,
                mosTemperatureC = 25,
            ))
            dao.upsertSolarDay(SolarDailyRollupEntity("2026-10-15", 0.0, 1.0, 10_000))
            dao.insertCapacityTest(CapacityTestEntity(state = "STOPPED", startedAtMs = 1_000))
            dao.insertAlertEvents(listOf(AlertEventEntity(timestampEpochMs = 1_500, type = "test", title = "test", message = "test")))

            GentaiTelemetryRepository(db).deleteTelemetryHistory(preferences)

            assertEquals(0, rowCount(db, "telemetry_samples"))
            assertEquals(0, rowCount(db, "connection_sessions"))
            assertEquals(0, rowCount(db, "solar_daily_rollups"))
            assertEquals(1, rowCount(db, "capacity_tests"))
            assertEquals(1, rowCount(db, "alert_events"))
            assertTrue(preferences.bundledCapacityImportDisabled)
            assertTrue(preferences.settings.value.trackingStartMs > 2_000)
            val failure = runCatching {
                CapacityTestRepository(db).importCsv(StringReader("")) { !preferences.bundledCapacityImportDisabled }
            }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertEquals(0, rowCount(db, "telemetry_samples"))
        } finally {
            db.close()
        }
    }

    @Test fun deleteBeforeTodayKeepsTodaySamplesSessionsAndFlowRecordings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TelemetryDatabase::class.java).build()
        try {
            val dao = db.telemetryDao()
            val preferences = EnergyPreferences.isolatedForTest(InstrumentationRegistry.getInstrumentation().context)
            val now = System.currentTimeMillis()
            val zone = preferences.settings.value.zone
            val todayStart = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val oldSession = dao.insertSession(ConnectionSessionEntity(startedAtEpochMs = todayStart - 86_400_000L, endedAtEpochMs = todayStart - 86_000_000L))
            val todaySession = dao.insertSession(ConnectionSessionEntity(startedAtEpochMs = todayStart + 1_000L, endedAtEpochMs = now))
            val oldFlow = dao.insertFlowRecording(FlowRecordingEntity(mode = "CHARGING", startedAtMs = todayStart - 86_400_000L, endedAtMs = todayStart - 86_000_000L))
            val todayFlow = dao.insertFlowRecording(FlowRecordingEntity(mode = "CHARGING", startedAtMs = todayStart + 1_000L, endedAtMs = now))
            dao.insertSample(testSample(oldSession, todayStart - 86_300_000L, oldFlow))
            dao.insertSample(testSample(todaySession, todayStart + 2_000L, todayFlow))

            GentaiTelemetryRepository(db).deleteTelemetryBeforeKeepingToday(preferences, now)

            assertEquals(1, rowCount(db, "telemetry_samples"))
            assertEquals(1, rowCount(db, "connection_sessions"))
            assertEquals(1, rowCount(db, "flow_recordings"))
            assertEquals(todayStart, preferences.settings.value.trackingStartMs)
            db.openHelper.readableDatabase.query("SELECT recordingId FROM telemetry_samples").use { cursor ->
                cursor.moveToFirst()
                assertEquals(todayFlow, cursor.getLong(0))
            }
        } finally {
            db.close()
        }
    }

    private fun testSample(sessionId: Long, timestampMs: Long, recordingId: Long) = TelemetrySampleEntity(
        timestampEpochMs = timestampMs,
        connectionSessionId = sessionId,
        voltageV = 52.0,
        currentA = 2.0,
        powerW = 104.0,
        socPct = 80,
        sohPct = 100,
        remainingAh = 80.0,
        fullAh = 100.0,
        cycleCount = 1,
        cellVoltagesV = "",
        cellMinV = null,
        cellMaxV = null,
        cellDeltaV = null,
        probeTemperaturesC = "",
        ambientTemperatureC = 25,
        mosTemperatureC = 25,
        recordingId = recordingId,
    )

    private fun rowCount(db: TelemetryDatabase, table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}

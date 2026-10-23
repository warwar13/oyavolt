package com.warwar13.gentai.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SolarMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TelemetryDatabase::class.java)

    @Test fun existingRecordingsSurviveAndImportedTestsAreExcluded() {
        helper.createDatabase("solar-migration-test", 5).apply {
            execSQL("INSERT INTO connection_sessions (id, startedAtEpochMs, endedAtEpochMs, endReason) VALUES (1, 1000, 2000, 'Imported capacity test')")
            execSQL("INSERT INTO connection_sessions (id, startedAtEpochMs, endedAtEpochMs, endReason) VALUES (2, 3000, 4000, 'User stopped monitoring')")
            close()
        }
        helper.runMigrationsAndValidate("solar-migration-test", 6, true, TelemetryDatabase.MIGRATION_5_6).use { db ->
            db.query("SELECT origin FROM connection_sessions ORDER BY id").use { cursor ->
                cursor.moveToFirst()
                assertEquals("IMPORTED", cursor.getString(0))
                cursor.moveToNext()
                assertEquals("RECORDED", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM solar_daily_rollups").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test fun typedFlowRecordingSchemaAddsModeAndSampleLink() {
        helper.createDatabase("flow-recording-migration-test", 6).apply {
            execSQL("INSERT INTO connection_sessions (id, startedAtEpochMs, endedAtEpochMs, endReason, origin) VALUES (1, 1000, 2000, 'User stopped monitoring', 'RECORDED')")
            execSQL("INSERT INTO telemetry_samples (timestampEpochMs, connectionSessionId, voltageV, currentA, powerW, socPct, sohPct, remainingAh, fullAh, cycleCount, cellVoltagesV, cellMinV, cellMaxV, cellDeltaV, probeTemperaturesC, ambientTemperatureC, mosTemperatureC, protectionBitfield, balanceMask, disconnectedCellMask, rssi, qualityFlags, gapFromPreviousMs) VALUES (1500, 1, 50.0, 5.0, 250.0, 50, 100, 50.0, 100.0, 1, '', NULL, NULL, NULL, '', 25, 25, NULL, NULL, NULL, NULL, 0, NULL)")
            close()
        }
        helper.runMigrationsAndValidate("flow-recording-migration-test", 7, true, TelemetryDatabase.MIGRATION_6_7).use { db ->
            db.query("SELECT COUNT(*) FROM flow_recordings").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT recordingId FROM telemetry_samples LIMIT 1").use { cursor ->
                cursor.moveToFirst()
                assertTrue(cursor.isNull(0))
            }
        }
    }
}

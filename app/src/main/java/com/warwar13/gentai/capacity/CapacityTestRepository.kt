package com.warwar13.gentai.capacity
import com.warwar13.gentai.data.CapacityTestEntity
import com.warwar13.gentai.data.CapacityTestSampleEntity
import com.warwar13.gentai.data.ConnectionSessionEntity
import com.warwar13.gentai.data.GentaiTelemetryRepository
import com.warwar13.gentai.data.TelemetryDatabase
import com.warwar13.gentai.data.TelemetrySampleEntity
import androidx.room.withTransaction
import java.io.Reader
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class CapacityTestSummary(val test: CapacityTestEntity, val result: CapacityResult, val sampleCount: Int)
data class CapacityImportResult(val testId: Long, val sampleCount: Int, val telemetryImported: Boolean)

class CapacityTestRepository(private val db: TelemetryDatabase) {
 suspend fun start(now:Long=System.currentTimeMillis()):Long { check(db.telemetryDao().activeCapacityTest()==null); return db.telemetryDao().insertCapacityTest(CapacityTestEntity(state="RUNNING",startedAtMs=now)) }
 suspend fun pause(id:Long, now:Long=System.currentTimeMillis())=db.telemetryDao().updateCapacityTest(id,"PAUSED",now,null)
 suspend fun resume(id:Long)=db.telemetryDao().resumeCapacityTest(id)
 suspend fun stop(id:Long, now:Long=System.currentTimeMillis())=db.telemetryDao().updateCapacityTest(id,"STOPPED",null,now)
 suspend fun record(test:CapacityTestEntity, point:CapacityPoint)=db.telemetryDao().insertCapacitySampleIfActive(CapacityTestSampleEntity(testId=test.id,timestampMs=point.timestampMs,voltageV=point.voltageV,currentA=point.currentA,socPct=point.socPct,segment=test.segment))
 suspend fun result(id:Long):CapacityResult = CapacityAnalytics.analyse(db.telemetryDao().capacitySamples(id).map { CapacityPoint(it.timestampMs,it.voltageV,it.currentA,it.socPct,segment=it.segment) })
 suspend fun recoverInProgress(): CapacityTestEntity? = db.telemetryDao().activeCapacityTest()

 fun observeSummaries(): Flow<List<CapacityTestSummary>> = db.telemetryDao().observeCapacityTests().map { tests ->
  tests.map { test ->
   val samples = db.telemetryDao().capacitySamples(test.id)
   CapacityTestSummary(test, CapacityAnalytics.analyse(samples.map { CapacityPoint(it.timestampMs, it.voltageV, it.currentA, it.socPct, segment = it.segment) }), samples.size)
  }
 }

 suspend fun importCsv(reader: Reader, shouldImport: () -> Boolean = { true }): CapacityImportResult = withContext(Dispatchers.IO) {
  var analyticsStartMs: Long? = null
  val result = reader.buffered().use { input ->
   db.withTransaction {
    check(shouldImport()) { "Bundled capacity import disabled after history reset" }
    val header = input.readLine()?.split(',') ?: error("Capacity CSV is empty")
    val columns = header.withIndex().associate { it.value.trim() to it.index }
    val required = listOf("test_started_at", "test_ended_at", "timestamp", "soc_percent", "voltage_v", "current_a")
    check(required.all(columns::containsKey)) { "Not a supported capacity-test CSV" }
    val firstLine = input.readLine() ?: error("Capacity CSV has no samples")
    val first = parseCapacityCsvRow(firstLine, columns)
    val existingTest = db.telemetryDao().capacityTestAt(first.startedAtMs)
    val existingSession = db.telemetryDao().connectionSessionAt(first.startedAtMs)
    check(existingTest == null || existingSession == null) { "This capacity test is already imported" }
    val testId = existingTest?.id ?: db.telemetryDao().insertCapacityTest(CapacityTestEntity(state = "STOPPED", startedAtMs = first.startedAtMs, endedAtMs = first.endedAtMs))
    val sessionId = existingSession?.id ?: db.telemetryDao().insertSession(ConnectionSessionEntity(startedAtEpochMs = first.startedAtMs, endedAtEpochMs = first.endedAtMs, endReason = "Imported capacity test", origin = "IMPORTED"))
    val importCapacity = existingTest == null
    val importTelemetry = existingSession == null
    if (importTelemetry) analyticsStartMs = first.startedAtMs
    var count = 0
    var previousTimestampMs: Long? = null
    val capacityBatch = ArrayList<CapacityTestSampleEntity>(500)
    val telemetryBatch = ArrayList<TelemetrySampleEntity>(500)
    fun append(row: ImportedCapacityRow) {
     if (importCapacity) capacityBatch += CapacityTestSampleEntity(testId = testId, timestampMs = row.timestampMs, voltageV = row.voltageV, currentA = row.currentA, socPct = row.socPct)
     if (importTelemetry) {
      val cellMin = row.cellsV.minOrNull()
      val cellMax = row.cellsV.maxOrNull()
      val elapsed = previousTimestampMs?.let { row.timestampMs - it }
      telemetryBatch += TelemetrySampleEntity(
       timestampEpochMs = row.timestampMs,
       connectionSessionId = sessionId,
       voltageV = row.voltageV,
       currentA = row.currentA,
       powerW = row.voltageV * row.currentA,
       socPct = row.socPct,
       sohPct = row.sohPct,
       remainingAh = row.remainingAh,
       fullAh = row.fullAh,
       cycleCount = 0,
       cellVoltagesV = row.cellsV.joinToString(","),
       cellMinV = cellMin,
       cellMaxV = cellMax,
       cellDeltaV = if (cellMin == null || cellMax == null) null else cellMax - cellMin,
       probeTemperaturesC = "",
       ambientTemperatureC = row.ambientC,
       mosTemperatureC = row.mosC,
       gapFromPreviousMs = elapsed?.takeIf { it > GentaiTelemetryRepository.GAP_THRESHOLD_MS },
      )
      previousTimestampMs = row.timestampMs
     }
     count++
    }
    suspend fun flush() {
     if (capacityBatch.isNotEmpty()) {
      db.telemetryDao().insertCapacitySamples(capacityBatch.toList())
      capacityBatch.clear()
     }
     if (telemetryBatch.isNotEmpty()) {
      db.telemetryDao().insertSamples(telemetryBatch.toList())
      telemetryBatch.clear()
     }
    }
    append(first)
    while (true) {
     val line = input.readLine() ?: break
     if (line.isBlank()) continue
     append(parseCapacityCsvRow(line, columns))
     if (capacityBatch.size >= 500 || telemetryBatch.size >= 500) flush()
    }
    flush()
    CapacityImportResult(testId, count, importTelemetry)
   }
  }
  analyticsStartMs?.let { GentaiTelemetryRepository(db).rebuildAnalytics(it) }
  result
 }
}

private data class ImportedCapacityRow(
 val startedAtMs: Long,
 val endedAtMs: Long,
 val timestampMs: Long,
 val socPct: Int,
 val sohPct: Int,
 val voltageV: Double,
 val currentA: Double,
 val remainingAh: Double,
 val fullAh: Double,
 val ambientC: Int,
 val mosC: Int,
 val cellsV: List<Double>,
)

private fun parseCapacityCsvRow(line: String, columns: Map<String, Int>): ImportedCapacityRow {
 val values = line.split(',')
 fun value(name: String) = values.getOrNull(columns.getValue(name))?.trim()?.takeIf(String::isNotEmpty) ?: error("Missing $name")
 fun optionalDouble(name: String) = columns[name]?.let(values::getOrNull)?.trim()?.toDoubleOrNull()
 return ImportedCapacityRow(
  startedAtMs = Instant.parse(value("test_started_at")).toEpochMilli(),
  endedAtMs = Instant.parse(value("test_ended_at")).toEpochMilli(),
  timestampMs = Instant.parse(value("timestamp")).toEpochMilli(),
  socPct = value("soc_percent").toDouble().toInt(),
  sohPct = optionalDouble("soh_percent")?.toInt() ?: 0,
  voltageV = value("voltage_v").toDouble(),
  currentA = value("current_a").toDouble(),
  remainingAh = optionalDouble("remaining_ah") ?: 0.0,
  fullAh = optionalDouble("full_ah") ?: 0.0,
  ambientC = optionalDouble("ambient_c")?.toInt() ?: 0,
  mosC = optionalDouble("mos_c")?.toInt() ?: 0,
  cellsV = (1..4).mapNotNull { optionalDouble("cell_${it}_v") },
 )
}

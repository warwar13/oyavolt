package com.warwar13.gentai.export

import com.warwar13.gentai.data.DailySummaryEntity
import com.warwar13.gentai.data.PowerSessionEntity
import com.warwar13.gentai.data.TelemetrySampleEntity
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object CsvExport {
 fun telemetry(rows: Sequence<TelemetrySampleEntity>): Sequence<String> = sequence { yield("timestamp_utc,session_id,voltage_v,current_a,power_w,soc_pct,soh_pct,remaining_ah,full_ah,cycle_count,cells_v,cell_min_v,cell_max_v,cell_delta_v,probe_temperatures_c,ambient_c,mos_c,protection_bitfield,balance_mask,disconnected_cell_mask,rssi,quality_flags,gap_ms") ; rows.forEach { yield(listOf(iso(it.timestampEpochMs),it.connectionSessionId,it.voltageV,it.currentA,it.powerW,it.socPct,it.sohPct,it.remainingAh,it.fullAh,it.cycleCount,q(it.cellVoltagesV),it.cellMinV ?: "",it.cellMaxV ?: "",it.cellDeltaV ?: "",q(it.probeTemperaturesC),it.ambientTemperatureC,it.mosTemperatureC,it.protectionBitfield ?: "",it.balanceMask ?: "",it.disconnectedCellMask ?: "",it.rssi ?: "",it.qualityFlags,it.gapFromPreviousMs ?: "").joinToString(",")) } }
 fun sessions(rows: Sequence<PowerSessionEntity>): Sequence<String> = sequence { yield("mode,start_utc,end_utc,start_soc_pct,end_soc_pct,charged_ah,discharged_ah,charged_wh,discharged_wh,average_power_w,peak_power_w,sample_count,coverage_pct"); rows.forEach { yield(listOf(it.mode,iso(it.startEpochMs),iso(it.endEpochMs),it.startSocPct ?: "",it.endSocPct ?: "",it.chargedAh,it.dischargedAh,it.chargedWh,it.dischargedWh,it.averagePowerW,it.peakAbsolutePowerW,it.sampleCount,it.coveragePct).joinToString(",")) } }
 fun daily(rows: Sequence<DailySummaryEntity>): Sequence<String> = sequence { yield("day_start_utc,charged_ah,discharged_ah,charged_wh,discharged_wh,observed_ms,missing_ms,coverage_pct,sample_count"); rows.forEach { yield(listOf(iso(it.dayStartEpochMs),it.chargedAh,it.dischargedAh,it.chargedWh,it.dischargedWh,it.observedMs,it.missingMs,it.coveragePct,it.sampleCount).joinToString(",")) } }
 fun filename(kind:String, now:Long=System.currentTimeMillis())="oyavolt-$kind-${DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(now))}.csv"
 private fun iso(ms:Long)=DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ms)); private fun q(v:String)="\"${v.replace("\"","\"\"")}\""
}

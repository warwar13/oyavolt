package com.warwar13.gentai.export
import com.warwar13.gentai.data.TelemetrySampleEntity
import org.junit.Assert.assertTrue
import org.junit.Test
class CsvExportTest { @Test fun `raw export retains signed values gaps and quoted cells`() { val row=TelemetrySampleEntity(timestampEpochMs=0,connectionSessionId=7,voltageV=12.8,currentA=-2.5,powerW=-32.0,socPct=50,sohPct=99,remainingAh=50.0,fullAh=100.0,cycleCount=2,cellVoltagesV="3.2,3.2",cellMinV=3.2,cellMaxV=3.2,cellDeltaV=0.0,probeTemperaturesC="20,21",ambientTemperatureC=20,mosTemperatureC=21,gapFromPreviousMs=60000); val csv=CsvExport.telemetry(sequenceOf(row)).toList().last(); assertTrue(csv.contains(",-2.5,-32.0,")); assertTrue(csv.contains("\"3.2,3.2\"")); assertTrue(csv.endsWith(",60000")) } }

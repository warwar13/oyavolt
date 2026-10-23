package com.warwar13.gentai.capacity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class CapacityAnalyticsTest {
 private fun p(t:Long,a:Double)=CapacityPoint(t,12.8,a,50)
 @Test fun normalDischarge() { val r=CapacityAnalytics.analyse(listOf(p(0,-10.0),p(5_000,-10.0))); assertEquals(10.0/720, r.dischargedAh,1e-8); assertEquals(0.0,r.chargedAh,0.0) }
 @Test fun normalCharge() { val r=CapacityAnalytics.analyse(listOf(p(0,10.0),p(5_000,10.0))); assertEquals(10.0/720,r.chargedAh,1e-8); assertEquals(0.0,r.dischargedAh,0.0) }
 @Test fun gapsAreNeverIntegrated() { val r=CapacityAnalytics.analyse(listOf(p(0,-10.0),p(60_000,-10.0))); assertEquals(0.0,r.dischargedWh,0.0); assertEquals(60_000,r.missingMs); assertEquals(0.0,r.coveragePct,0.0) }
 @Test fun mixedRunSeparatesTotals() { val r=CapacityAnalytics.analyse(listOf(p(0,-10.0),p(5_000,-10.0),p(10_000,10.0))); assertTrue(r.dischargedAh>0); assertTrue(r.chargedAh>0) }
 @Test fun irregularIntervalsUseTimeWeightedAveragePower() { val r=CapacityAnalytics.analyse(listOf(p(0,-10.0),p(5_000,-10.0),p(25_000,-20.0))); assertEquals((128.0*5+192.0*20)/25,r.averagePowerW,1e-8) }
 @Test fun pauseResumeNeverIntegratesEvenWhenShort() { val r=CapacityAnalytics.analyse(listOf(p(0,-10.0),p(5_000,-10.0),CapacityPoint(10_000,12.8,-10.0,50,segment=1),CapacityPoint(15_000,12.8,-10.0,50,segment=1))); assertEquals(10.0/360.0,r.dischargedAh,1e-8); assertEquals(5_000,r.missingMs) }
}

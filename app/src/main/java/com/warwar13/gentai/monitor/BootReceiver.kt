package com.warwar13.gentai.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val address = MonitoringRestorationPolicy.addressForBoot(MonitoringPreferences(context).read()) ?: return
        runCatching { MonitorService.start(context, address) }
    }
}

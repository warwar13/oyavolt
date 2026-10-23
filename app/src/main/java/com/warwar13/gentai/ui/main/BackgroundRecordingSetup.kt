package com.warwar13.gentai.ui.main

import android.content.ActivityNotFoundException
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
@SuppressLint("BatteryLife") // User-requested, continuous BLE logger: Doze breaks the core function.
internal fun BackgroundRecordingSetup() {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var unrestricted by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    var message by remember { mutableStateOf<String?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        unrestricted = power.isIgnoringBatteryOptimizations(context.packageName)
    }
    fun open(intent: Intent, fallback: Intent? = null) {
        try {
            context.startActivity(intent)
            message = null
        } catch (_: ActivityNotFoundException) {
            if (fallback != null) {
                try { context.startActivity(fallback); message = null }
                catch (_: Exception) { message = "Open phone Settings > Apps > OyaVolt > Battery manually." }
            } else message = "Open phone Settings > Apps > OyaVolt > Battery manually."
        } catch (_: SecurityException) {
            message = "Open phone Settings > Apps > OyaVolt > Battery manually."
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (unrestricted) "Android battery optimization: exempt"
            else "Setup needed: Android battery optimization can pause overnight recording.",
            style = MaterialTheme.typography.bodySmall,
            color = if (unrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        if (!unrestricted) {
            Text("Allow OyaVolt to run in the background on the next system screen. This is needed for continuous BLE recording during deep sleep.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                // MIUI otherwise routes this action to its separate app battery saver.
                open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")).setPackage("com.android.settings"),
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
            }) {
                Text("Allow screen-off recording")
            }
        }
        Text("On Poco/Xiaomi also set Battery saver to No restrictions and allow Autostart in the app's system settings. These manufacturer settings cannot be verified here.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) { Text("Open OyaVolt app settings") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

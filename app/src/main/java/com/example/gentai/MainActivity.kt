package com.warwar13.gentai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.warwar13.gentai.capacity.CapacityTestRepository
import com.warwar13.gentai.analytics.EnergyPreferences
import com.warwar13.gentai.data.TelemetryDatabase
import com.warwar13.gentai.theme.GentaiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        val energyPreferences = EnergyPreferences.get(applicationContext)
        if (!energyPreferences.bundledCapacityImportDisabled) {
          assets.open(BUNDLED_CAPACITY_TEST).reader().use {
            CapacityTestRepository(TelemetryDatabase.get(applicationContext)).importCsv(it) {
              !energyPreferences.bundledCapacityImportDisabled
            }
          }
        }
      }
    }

    enableEdgeToEdge()
    setContent {
      GentaiTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }

  private companion object {
    const val BUNDLED_CAPACITY_TEST = "gentai-capacity-test-2026-09-14T02-05-11-802Z.csv"
  }
}

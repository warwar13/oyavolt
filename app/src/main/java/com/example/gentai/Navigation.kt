package com.warwar13.gentai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.warwar13.gentai.ui.main.MainScreen
import com.warwar13.gentai.ui.main.HistoryScreen
import com.warwar13.gentai.ui.main.AnalyticsScreen
import com.warwar13.gentai.ui.main.AlertsScreen
import com.warwar13.gentai.ui.main.SolarSettingsScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)
  val active = backStack.lastOrNull() ?: Main
  fun select(destination: NavKey) {
    if (active == destination) return
    backStack.clear()
    backStack.add(destination)
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Box(
        Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surfaceContainerLow)
          .navigationBarsPadding(),
      ) {
        NavigationBar(
          modifier = Modifier.fillMaxWidth().height(68.dp),
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          tonalElevation = 0.dp,
          windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
          NavigationBarItem(selected = active == Main, onClick = { select(Main) }, icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("Home") }, colors = itemColors)
          NavigationBarItem(selected = active == Analytics, onClick = { select(Analytics) }, icon = { Icon(Icons.Rounded.Timeline, null) }, label = { Text("Analytics") }, colors = itemColors)
          NavigationBarItem(selected = active == History, onClick = { select(History) }, icon = { Icon(Icons.Rounded.History, null) }, label = { Text("History") }, colors = itemColors)
          NavigationBarItem(selected = active == Alerts, onClick = { select(Alerts) }, icon = { Icon(Icons.Rounded.Notifications, null) }, label = { Text("Alerts") }, colors = itemColors)
        }
      }
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(Modifier.fillMaxSize().padding(innerPadding)) {
      NavDisplay(
        backStack = backStack,
        onBack = { select(Main) },
        entryProvider = entryProvider {
          entry<Main> { MainScreen(onSettings = { select(Settings) }) }
          entry<Analytics> { AnalyticsScreen(onSettings = { select(Settings) }) }
          entry<History> { HistoryScreen(onBack = { select(Main) }) }
          entry<Alerts> { AlertsScreen() }
          entry<Settings> { SolarSettingsScreen(onBack = { select(Main) }) }
        },
      )
    }
  }
}

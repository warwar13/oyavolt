package com.warwar13.gentai.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Basic smoke test for the current OyaVolt dashboard. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent { MainScreen() }
  }

  @Test
  fun dashboardAndMonitoringControlsExist() {
    composeTestRule.onNodeWithText("OyaVolt").assertExists()
    composeTestRule.onNodeWithText("Battery monitor").assertExists()
    composeTestRule.onNodeWithText("Solar battery journal").assertExists()
  }
}

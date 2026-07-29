package com.inventory.mobile.ui

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inventory.mobile.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BarcodeScannerSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensCameraAndProcessesFramesWithoutCrashing() {
        composeRule.activity.setContent {
            MaterialTheme {
                BarcodeScannerDialog(onResult = {}, onDismiss = {})
            }
        }

        SystemClock.sleep(5_000)
        assertFalse(composeRule.activity.isFinishing)
    }
}

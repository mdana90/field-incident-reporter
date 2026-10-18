package com.company.fieldincidentreporter

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportFormUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun emptySendShowsPhotoAndSeverityGuidance() {
        composeRule.onNodeWithText("+  Report incident").performClick()
        composeRule.onNodeWithText("Send report").performClick()
        composeRule.onNodeWithText("Choose 1 to 3 photos").assertExists()
        composeRule.onNodeWithText("Choose a severity").assertExists()
    }
}

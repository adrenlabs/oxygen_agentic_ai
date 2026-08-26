package com.oxygen.ai

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class OxygenUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsBrand() {
        rule.onNodeWithText("OXYGEN AI").assertIsDisplayed()
    }

    @Test
    fun openChatDestination() {
        rule.onNodeWithText("Open chat").performClick()
        rule.onNodeWithText("Message OXYGEN…").assertIsDisplayed()
    }
}

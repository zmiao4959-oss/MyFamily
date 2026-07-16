package cn.jiayi.familymemory.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import cn.jiayi.familymemory.R
import cn.jiayi.familymemory.ui.theme.FamilyMemoryTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FamilyVisualsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyStateExplainsNextStepAndActionIsReachable() {
        var clicked = false
        compose.setContent {
            FamilyMemoryTheme {
                EmptyState(R.drawable.empty_people, "从第一位家人开始", "添加本人或家人", actionLabel = "添加第一位家人", onAction = { clicked = true })
            }
        }
        compose.onNodeWithText("从第一位家人开始").assertIsDisplayed()
        compose.onNodeWithText("添加第一位家人").performClick()
        assertTrue(clicked)
    }

    @Test
    fun errorBannerOffersRetry() {
        var retried = false
        compose.setContent { FamilyMemoryTheme { StatusBanner("连接失败", true, onRetry = { retried = true }) } }
        compose.onNodeWithText("连接失败").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        assertTrue(retried)
    }
}

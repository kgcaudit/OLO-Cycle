package com.kgcaudit.olocycle.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kgcaudit.olocycle.Stepper
import com.kgcaudit.olocycle.ui.theme.OloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 컴팩트 조절바(Stepper) 회귀 방지: 값+단위 표기, +/− 증감, min/max 클램프.
 * 클램프를 없애거나 단위 표기를 빼면 이 테스트가 잡는다(돌연변이 확인 대상).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-xhdpi", sdk = [34])
class StepperTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shows_value_with_unit() {
        rule.setContent { OloTheme { Stepper(Modifier, "평균 주기", 28, 15, 60, "일") {} } }
        rule.onNodeWithText("28일").assertIsDisplayed()
    }

    @Test
    fun plus_increments_and_clamps_at_max() {
        var value = 59
        rule.setContent {
            OloTheme {
                var v by remember { mutableIntStateOf(value) }
                Stepper(Modifier, "주기", v, 15, 60, "일") { v = it; value = it }
            }
        }
        rule.onNodeWithText("+").performClick()
        rule.runOnIdle { assertEquals(60, value) }
        rule.onNodeWithText("+").performClick() // 최대 초과 시도 → 60 유지
        rule.runOnIdle { assertEquals(60, value) }
    }

    @Test
    fun minus_clamps_at_min() {
        var value = 15
        rule.setContent {
            OloTheme {
                var v by remember { mutableIntStateOf(value) }
                Stepper(Modifier, "주기", v, 15, 60, "일") { v = it; value = it }
            }
        }
        rule.onNodeWithText("−").performClick() // 최소 미만 시도 → 15 유지
        rule.runOnIdle { assertEquals(15, value) }
    }
}

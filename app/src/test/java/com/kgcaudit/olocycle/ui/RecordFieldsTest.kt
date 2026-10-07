package com.kgcaudit.olocycle.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kgcaudit.olocycle.DayDetailPanel
import com.kgcaudit.olocycle.data.DayRecord
import com.kgcaudit.olocycle.ui.theme.OloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 1단계 기록 입력 확장 회귀: 기록 화면에 '생리 종료일' 토글과 '체중 (kg)' 입력이 보이고,
 * 기존 체중 값이 채워진다. 둘 중 하나를 빼면 이 테스트가 잡는다(돌연변이 확인 대상).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w732dp-h1100dp-xxhdpi", sdk = [34])
class RecordFieldsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shows_period_end_toggle_and_weight_field() {
        val date = LocalDate.of(2026, 9, 30)
        rule.setContent {
            OloTheme {
                DayDetailPanel(
                    Modifier.fillMaxSize(), date, isPeriodStart = false, isPeriodEnd = false,
                    existing = DayRecord(profileId = 1, date = date, weight = 55.2),
                ) { _, _, _, _, _, _, _, _, _ -> }
            }
        }
        rule.onNodeWithText("생리 종료일").assertIsDisplayed()
        rule.onNodeWithText("체중 (kg)").assertIsDisplayed()
        rule.onNodeWithText("55.2").assertIsDisplayed()
    }
}

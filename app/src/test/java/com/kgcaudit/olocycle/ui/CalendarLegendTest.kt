package com.kgcaudit.olocycle.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.kgcaudit.olocycle.PhaseLegend
import com.kgcaudit.olocycle.ui.theme.OloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 달력 범례 문구 회귀 방지: 예측일은 앱 전체에서 "예정"으로 통일한다(달력 칸·히어로와 일치).
 * 범례가 예전 "예측"으로 되돌아가면 이 테스트가 잡는다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-xhdpi", sdk = [34])
class CalendarLegendTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun legend_uses_expected_wording() {
        rule.setContent { OloTheme { PhaseLegend() } }
        rule.onNodeWithText("생리").assertExists()
        rule.onNodeWithText("예정").assertExists()
        rule.onNodeWithText("가임기").assertExists()
        rule.onNodeWithText("배란").assertExists()
        // 예전 표기 "예측"은 없어야 한다(칸 라벨은 "예정").
        rule.onAllNodesWithText("예측").assertCountEquals(0)
    }
}

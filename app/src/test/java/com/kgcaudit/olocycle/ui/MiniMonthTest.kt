package com.kgcaudit.olocycle.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kgcaudit.olocycle.MiniMonth
import com.kgcaudit.olocycle.cycle.Phase
import com.kgcaudit.olocycle.ui.theme.OloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * 연 보기 미니 달력 회귀 방지: 모든 달을 6주(42칸)로 채워 세로 늘림 없이 높이가 같아야 한다.
 * 주 수가 다른 두 달(5주짜리 2월 vs 6주짜리 8월)을 같은 너비로 그렸을 때 높이가 같다.
 * 42칸 패딩을 빼면 달마다 높이가 달라져 이 테스트가 잡는다(돌연변이 확인 대상).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-xhdpi", sdk = [34])
class MiniMonthTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun all_months_same_height() {
        val today = LocalDate.of(2026, 6, 15)
        rule.setContent {
            OloTheme {
                Row {
                    Box(Modifier.width(120.dp).testTag("feb")) {
                        MiniMonth(YearMonth.of(2026, 2), today, Color(0xFFB95B3B), { Phase.UNKNOWN }, {})
                    }
                    Box(Modifier.width(120.dp).testTag("aug")) {
                        MiniMonth(YearMonth.of(2026, 8), today, Color(0xFFB95B3B), { Phase.UNKNOWN }, {})
                    }
                }
            }
        }
        val feb = rule.onNodeWithTag("feb").getUnclippedBoundsInRoot()
        rule.onNodeWithTag("aug").assertHeightIsEqualTo(feb.bottom - feb.top)
    }
}

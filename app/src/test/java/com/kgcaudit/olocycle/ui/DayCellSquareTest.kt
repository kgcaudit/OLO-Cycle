package com.kgcaudit.olocycle.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kgcaudit.olocycle.DayCell
import com.kgcaudit.olocycle.cycle.Phase
import com.kgcaudit.olocycle.ui.theme.OloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 달력 날짜 칸 회귀 방지: 기본(fill=false)에서 칸은 정사각형(너비=높이)이라 인위적 세로 늘림이 없다.
 * aspectRatio(1f) 를 없애거나 바꾸면 높이가 너비와 달라져 이 테스트가 잡는다(돌연변이 확인 대상).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-xhdpi", sdk = [34])
class DayCellSquareTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun day_cell_is_square_by_default() {
        rule.setContent {
            OloTheme {
                Box(Modifier.width(60.dp).testTag("cell")) {
                    DayCell(
                        day = LocalDate.of(2026, 9, 15), isToday = false, profileColor = Color(0xFFB95B3B),
                        phase = Phase.UNKNOWN, record = null, label = null, enabled = true, onClick = {}, fill = false,
                    )
                }
            }
        }
        rule.onNodeWithTag("cell").assertWidthIsEqualTo(60.dp).assertHeightIsEqualTo(60.dp)
    }
}

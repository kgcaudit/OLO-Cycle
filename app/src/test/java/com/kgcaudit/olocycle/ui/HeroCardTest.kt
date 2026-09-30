package com.kgcaudit.olocycle.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kgcaudit.olocycle.HeroCard
import com.kgcaudit.olocycle.HomeState
import com.kgcaudit.olocycle.cycle.CycleParams
import com.kgcaudit.olocycle.cycle.CyclePrediction
import com.kgcaudit.olocycle.ui.theme.OloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 홈 히어로 회귀 방지: 주기 진행이 히어로 안에 통합돼 D-day·다음 예정·진행 정보(N일째·주기 길이)가
 * 함께 보여야 한다. 진행 표기를 빼면 이 테스트가 잡는다(돌연변이 확인 대상).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-xhdpi", sdk = [34])
class HeroCardTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun state(): HomeState {
        val start = LocalDate.of(2026, 9, 28)
        return HomeState(
            periodStarts = listOf(start),
            prediction = CyclePrediction(
                periodStart = start, periodEnd = start.plusDays(4),
                fertileStart = LocalDate.of(2026, 10, 6), fertileEnd = LocalDate.of(2026, 10, 12),
                ovulation = LocalDate.of(2026, 10, 11), nextPeriodStart = LocalDate.of(2026, 10, 25),
                basedOnActualStart = true,
            ),
            daysUntilNextPeriod = 25,
            params = CycleParams(cycleLength = 27, periodLength = 5, lutealLength = 14),
            cycleDayIndex = 3,
            today = LocalDate.of(2026, 9, 30),
        )
    }

    @Test
    fun hero_shows_dday_and_integrated_progress() {
        rule.setContent { OloTheme { HeroCard(state(), Color(0xFF5B6E86)) } }
        rule.onNodeWithText("D-25").assertExists()
        rule.onNodeWithText("다음 생리 10/25 예정").assertExists()
        // 히어로에 통합된 주기 진행 표기
        rule.onNodeWithText("3일째").assertExists()
        rule.onNodeWithText("27일 주기").assertExists()
    }
}

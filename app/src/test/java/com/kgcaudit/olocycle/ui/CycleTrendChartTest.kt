package com.kgcaudit.olocycle.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kgcaudit.olocycle.CycleTrendChart
import com.kgcaudit.olocycle.ui.theme.OloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 주기 추이 차트 회귀 방지: 각 주기 값 라벨과 평균 표기가 실제 데이터와 일치해야 한다.
 * 평균 계산/라벨을 깨뜨리면 이 테스트가 잡는다(돌연변이 확인 대상).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-xhdpi", sdk = [34])
class CycleTrendChartTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shows_values_and_average() {
        rule.setContent { OloTheme { CycleTrendChart(listOf(28, 26, 29, 27, 30), Color(0xFF5B6E86)) } }
        // 값 라벨(평균 30을 빼고 고유값 확인; 27·26·29·28은 유일)
        listOf("26", "29", "27", "30").forEach { rule.onNodeWithText(it).assertExists() }
        // 평균 = (28+26+29+27+30)/5 = 28
        rule.onNodeWithText("점선 = 평균 28일").assertExists()
    }
}

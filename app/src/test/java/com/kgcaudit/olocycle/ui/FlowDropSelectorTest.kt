package com.kgcaudit.olocycle.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kgcaudit.olocycle.FlowDropSelector
import com.kgcaudit.olocycle.ui.theme.OloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 생리량 물방울 선택기 회귀 방지: 4단계(없음·적음·보통·많음)가 모두 있어야 한다.
 * 한 단계라도 빠뜨리면 이 테스트가 잡는다(돌연변이 확인 대상).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-xhdpi", sdk = [34])
class FlowDropSelectorTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shows_four_flow_levels() {
        rule.setContent { OloTheme { FlowDropSelector(selected = 2) {} } }
        listOf("없음", "적음", "보통", "많음").forEach { rule.onNodeWithText(it).assertExists() }
    }
}

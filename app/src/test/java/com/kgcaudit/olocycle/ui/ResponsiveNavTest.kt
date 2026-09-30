package com.kgcaudit.olocycle.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.kgcaudit.olocycle.ResponsiveNav
import com.kgcaudit.olocycle.Tab
import com.kgcaudit.olocycle.ui.theme.OloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 반응형 뼈대 회귀 방지: 넓은 화면(expanded=true)이면 왼쪽 레일, 좁은 화면이면 하단 바가 나와야 한다.
 * expanded 분기를 뒤집거나 한쪽 내비를 지우면 이 테스트가 잡는다(돌연변이 확인 대상).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w732dp-h970dp-xxhdpi", sdk = [34])
class ResponsiveNavTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun setNav(expanded: Boolean) {
        rule.setContent {
            OloTheme {
                ResponsiveNav(
                    expanded = expanded, tab = Tab.HOME, accent = Color(0xFFB95B3B),
                    onSelect = {}, onFab = {},
                    header = { Text("헤더") },
                ) { m -> Box(m.fillMaxSize()) { Text("본문") } }
            }
        }
    }

    @Test
    fun expanded_shows_side_rail_not_bottom_bar() {
        setNav(expanded = true)
        rule.onNodeWithTag("rail").assertIsDisplayed()
        rule.onNodeWithTag("bottombar").assertDoesNotExist()
    }

    @Test
    fun compact_shows_bottom_bar_not_rail() {
        setNav(expanded = false)
        rule.onNodeWithTag("bottombar").assertIsDisplayed()
        rule.onNodeWithTag("rail").assertDoesNotExist()
    }
}

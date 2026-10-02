package com.kgcaudit.olocycle.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kgcaudit.olocycle.MemberSwitcher
import com.kgcaudit.olocycle.data.Profile
import com.kgcaudit.olocycle.ui.theme.OloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 상단 헤더 회귀 방지: 화면명(오늘/달력/분석)이 주 타이틀로 보이고, "OLO Cycle" 워드마크는 없어야 한다.
 * 워드마크를 되살리거나 화면명을 빼면 이 테스트가 잡는다(돌연변이 확인 대상).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-xhdpi", sdk = [34])
class HeaderTitleTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val profiles = listOf(Profile(id = 1, name = "현정", color = 0xFFB95B3B.toInt()))

    private fun setHeader(tabLabel: String, tabSubtitle: String? = null) {
        rule.setContent {
            OloTheme {
                MemberSwitcher(
                    tabLabel = tabLabel, profiles = profiles, selectedId = 1,
                    onSelect = {}, onAdd = {}, onEditCurrent = {}, onSettings = {},
                    tabSubtitle = tabSubtitle,
                )
            }
        }
    }

    @Test
    fun shows_screen_name_as_title() {
        setHeader("분석")
        rule.onNodeWithText("분석").assertIsDisplayed()
    }

    @Test
    fun no_wordmark() {
        setHeader("오늘")
        rule.onNodeWithText("OLO", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Cycle", substring = true).assertDoesNotExist()
    }

    /** 안 A: 화면명 아래 보조 맥락줄이 함께 보여야 한다. 맥락줄을 빼면 이 테스트가 잡는다(돌연변이 확인 대상). */
    @Test
    fun shows_context_subtitle() {
        setHeader("오늘", tabSubtitle = "9월 30일 화요일")
        rule.onNodeWithText("9월 30일 화요일").assertIsDisplayed()
    }
}

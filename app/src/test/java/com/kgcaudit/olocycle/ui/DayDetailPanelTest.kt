package com.kgcaudit.olocycle.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kgcaudit.olocycle.DayDetailPanel
import com.kgcaudit.olocycle.ui.theme.OloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 태블릿 달력 마스터-디테일 인라인 편집 패널 회귀 방지:
 * ① 날짜가 있으면 다이얼로그 없이 편집 필드(생리량·증상)와 저장 버튼이 바로 보인다.
 * ② 날짜가 없으면 안내만 보이고 편집 UI는 없다.
 * ③ 저장을 누르면 그 날짜로 onSave 가 불린다.
 * 빈 상태 분기를 없애거나 저장 배선을 끊으면 잡힌다(돌연변이 확인 대상).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w732dp-h970dp-xxhdpi", sdk = [34])
class DayDetailPanelTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shows_inline_editor_for_a_date() {
        rule.setContent {
            OloTheme {
                DayDetailPanel(Modifier.fillMaxSize(), LocalDate.of(2026, 9, 30), false, null) { _, _, _, _, _, _, _ -> }
            }
        }
        rule.onNodeWithText("9월 30일 기록").assertExists()
        rule.onNodeWithText("생리량").assertExists()
        rule.onNodeWithText("저장").assertExists()
    }

    @Test
    fun shows_prompt_when_no_date() {
        rule.setContent {
            OloTheme {
                DayDetailPanel(Modifier.fillMaxSize(), null, false, null) { _, _, _, _, _, _, _ -> }
            }
        }
        rule.onNodeWithText("날짜를 선택하면", substring = true).assertExists()
        rule.onNodeWithText("저장").assertDoesNotExist()
    }

    @Test
    fun save_invokes_callback_with_date() {
        var savedDate: LocalDate? = null
        val target = LocalDate.of(2026, 9, 15)
        rule.setContent {
            OloTheme {
                DayDetailPanel(Modifier.fillMaxSize(), target, false, null) { d, _, _, _, _, _, _ -> savedDate = d }
            }
        }
        rule.onNodeWithText("저장").performClick()
        assertTrue("저장을 누르면 onSave 가 불려야 한다", savedDate != null)
        assertEquals(target, savedDate)
    }
}

package com.kgcaudit.olocycle.ui

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kgcaudit.olocycle.SettingsGroupCard
import com.kgcaudit.olocycle.SettingsIconRow
import com.kgcaudit.olocycle.ui.theme.OloColors
import com.kgcaudit.olocycle.ui.theme.OloTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 설정 아이콘 리스트 행(나안 B) 회귀 방지:
 * ① 제목과 부제가 함께 보인다(부제를 빼면 잡힌다).
 * ② onClick 을 준 행은 눌러지고 콜백이 불린다(클릭 배선을 끊으면 잡힌다).
 * 둘 다 돌연변이 확인 대상.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-xhdpi", sdk = [34])
class SettingsIconRowTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shows_title_and_subtitle() {
        rule.setContent {
            OloTheme {
                SettingsGroupCard {
                    SettingsIconRow(Icons.Default.ChevronRight, "앱 정보", "버전·오픈소스 라이선스") {
                        Icon(Icons.Default.ChevronRight, null, tint = OloColors.Muted)
                    }
                }
            }
        }
        rule.onNodeWithText("앱 정보").assertExists()
        rule.onNodeWithText("버전·오픈소스 라이선스").assertExists()
    }

    @Test
    fun clickable_row_invokes_callback() {
        var clicked = false
        rule.setContent {
            OloTheme {
                SettingsGroupCard {
                    SettingsIconRow(Icons.Default.ChevronRight, "백업에서 복원", "현재 데이터를 대체",
                        onClick = { clicked = true }) {
                        Icon(Icons.Default.ChevronRight, null, tint = OloColors.Muted)
                    }
                }
            }
        }
        rule.onNodeWithText("백업에서 복원").performClick()
        assertTrue("행을 누르면 onClick 이 불려야 한다", clicked)
    }
}

package com.kgcaudit.olocycle.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.kgcaudit.olocycle.ui.theme.OloButtonShape
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 버튼 모양 회귀 방지: OLO 계열 버튼 공용 모서리는 small = 10.dp 여야 한다(알약 아님).
 * 값을 바꾸면 이 테스트가 잡는다(돌연변이 확인 대상). 값 변경은 OLO-Design 에 요청.
 */
class OloButtonShapeTest {
    @Test
    fun button_corner_is_10dp() {
        assertEquals(RoundedCornerShape(10.dp), OloButtonShape)
    }
}

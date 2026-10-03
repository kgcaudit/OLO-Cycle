package com.kgcaudit.olocycle.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 아이콘팩 회귀 방지: 각 OloIcons 벡터가 기대하는 경로(path) 개수를 그대로 담아야 한다.
 * 경로가 하나라도 빠지면(달력의 날짜 점, 잠금 고리 등) 글리프가 깨지므로 이 개수로 잡는다.
 * 또한 하단 내비 3종(집·달력·분석)은 OLO 톤의 '가는 선(stroke)' 글리프여야 한다 — 채움으로 되돌리면 잡는다.
 * 경로를 지우거나 선→채움으로 되돌리면 실패한다(돌연변이 확인 대상).
 */
class OloIconsTest {

    private fun paths(node: VectorNode): List<VectorPath> = when (node) {
        is VectorPath -> listOf(node)
        is VectorGroup -> node.flatMap { paths(it) }
        else -> emptyList()
    }

    private fun paths(vector: ImageVector): List<VectorPath> = paths(vector.root)

    @Test
    fun each_icon_keeps_its_paths() {
        val expected = mapOf(
            "Calendar" to 5, "Chart" to 3, "Home" to 3, "Settings" to 2, "Lock" to 3,
            "Shield" to 2, "Export" to 3, "Import" to 3, "Add" to 3, "Edit" to 2,
        )
        val actual = mapOf(
            "Calendar" to paths(OloIcons.Calendar).size,
            "Chart" to paths(OloIcons.Chart).size,
            "Home" to paths(OloIcons.Home).size,
            "Settings" to paths(OloIcons.Settings).size,
            "Lock" to paths(OloIcons.Lock).size,
            "Shield" to paths(OloIcons.Shield).size,
            "Export" to paths(OloIcons.Export).size,
            "Import" to paths(OloIcons.Import).size,
            "Add" to paths(OloIcons.Add).size,
            "Edit" to paths(OloIcons.Edit).size,
        )
        assertEquals(expected, actual)
    }

    @Test
    fun nav_icons_are_line_style() {
        // 집·분석: 모든 경로가 선(stroke). 달력: 틀·고리·헤더는 선, 오늘 점만 채움 → 선이 다수.
        assertTrue("집은 가는 선이어야 함", paths(OloIcons.Home).all { it.stroke != null && it.fill == null })
        assertTrue("분석은 가는 선이어야 함", paths(OloIcons.Chart).all { it.stroke != null && it.fill == null })
        assertTrue("달력 틀은 선이어야 함", paths(OloIcons.Calendar).count { it.stroke != null } >= 4)
    }
}

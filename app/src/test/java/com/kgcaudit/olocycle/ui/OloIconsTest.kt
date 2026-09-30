package com.kgcaudit.olocycle.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 아이콘팩 회귀 방지: 각 OloIcons 벡터가 원본 SVG 의 경로(path) 개수를 그대로 담아야 한다.
 * 경로가 하나라도 빠지면(달력의 날짜 점, 잠금 고리 등) 글리프가 깨지므로 이 개수로 잡는다.
 * 어떤 아이콘의 경로를 지우면 개수가 어긋나 실패한다(돌연변이 확인 대상).
 */
class OloIconsTest {

    private fun pathCount(node: VectorNode): Int = when (node) {
        is VectorPath -> 1
        is VectorGroup -> node.sumOf { pathCount(it) }
        else -> 0
    }

    private fun pathCount(vector: ImageVector): Int = pathCount(vector.root)

    @Test
    fun each_icon_keeps_its_paths() {
        val expected = mapOf(
            "Calendar" to 7, "Chart" to 3, "Home" to 4, "Settings" to 2, "Lock" to 3,
            "Shield" to 2, "Export" to 3, "Import" to 3, "Add" to 3, "Edit" to 2,
        )
        val actual = mapOf(
            "Calendar" to pathCount(OloIcons.Calendar),
            "Chart" to pathCount(OloIcons.Chart),
            "Home" to pathCount(OloIcons.Home),
            "Settings" to pathCount(OloIcons.Settings),
            "Lock" to pathCount(OloIcons.Lock),
            "Shield" to pathCount(OloIcons.Shield),
            "Export" to pathCount(OloIcons.Export),
            "Import" to pathCount(OloIcons.Import),
            "Add" to pathCount(OloIcons.Add),
            "Edit" to pathCount(OloIcons.Edit),
        )
        assertEquals(expected, actual)
    }
}

package com.kgcaudit.olocycle.cycle

import com.kgcaudit.olocycle.HomeState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * ⑥ 생리 종료일 기록 회귀: periodHistory 가 '기록된 실제 종료일'을 쓰고, 없으면 평균 생리기간 추정치를 쓴다.
 * periodEndByStart 반영을 되돌리면(항상 추정치) 이 테스트가 잡는다(돌연변이 확인 대상).
 */
class PeriodEndHistoryTest {

    @Test
    fun history_uses_recorded_end_when_present() {
        val d1 = LocalDate.of(2026, 9, 1)
        val d2 = LocalDate.of(2026, 9, 29)
        val state = HomeState(
            periodStarts = listOf(d1, d2),
            periodEndByStart = mapOf(d1 to LocalDate.of(2026, 9, 4)), // 실제 4일간(9/1~9/4)
            params = CycleParams(cycleLength = 28, periodLength = 5, lutealLength = 14),
        )
        // 최신순 정렬 → [d2(추정), d1(실제)]
        val items = state.periodHistory()
        val d1Item = items.first { it.start == d1 }
        val d2Item = items.first { it.start == d2 }
        assertEquals("실제 종료일 기록이 있으면 그 값", LocalDate.of(2026, 9, 4), d1Item.end)
        assertEquals("기록 없으면 평균 생리기간 추정(+4)", d2.plusDays(4), d2Item.end)
    }
}

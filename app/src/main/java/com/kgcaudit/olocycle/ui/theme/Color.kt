package com.kgcaudit.olocycle.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * OLO 디자인 시스템(밝은 테마) 값. crosspoint-reader의 인계 묶음 CpTheme 값을 그대로 옮겼다.
 * 따뜻한 클레이 브랜드 + 아이보리 웜톤 중립색이 이 계열 앱들을 한 식구로 묶는다.
 * (다크 모드는 다음 단계에서 토큰 한 곳으로 추가한다.)
 */
object OloColors {
    // 역할색
    val Background = Color(0xFFF7F4EF)
    val Surface = Color(0xFFFCFAF6)
    val SurfaceSoft = Color(0xFFEFEAE1) // 빈 칸·판 바탕(dialog 계열)
    val Ink = Color(0xFF1D1A16)         // 본문 글자(text)
    val Muted = Color(0xFF574D45)       // 보조 글자(textMuted)
    val Line = Color(0xFFD6CCC1)        // 구분선(divider)
    val Outline = Color(0xFF8B7F74)     // 테두리 단추 선

    val Primary = Color(0xFFB95B3B)     // 브랜드 클레이(accent)
    val OnPrimary = Color(0xFFFFFFFF)
    val Accent = Color(0xFFB95B3B)      // 별칭(그라데이션 등)
    val AccentContainer = Color(0xFFF6E0D6)
    val OnAccentContainer = Color(0xFF4A1E0C)
    val Error = Color(0xFFA50E2E)       // 삭제·오류(크림슨) — 브랜드와 색상 자체를 가른다

    // 주기 단계색 — 뜻이 있는 색. OLO 웜톤에 맞춰 재조율(한 곳에 정의).
    val Period = Color(0xFFC24A63)
    val PeriodLight = Color(0xFFF2DBE0)
    val Fertile = Color(0xFF3E7F80)
    val FertileLight = Color(0xFFD9EAEA)
    val Ovulation = Color(0xFF55606B)
    val OvulationLight = Color(0xFFDEE2E6)
    val Pms = Color(0xFFB07A8E)         // 웜 모브
    val PmsLight = Color(0xFFF5ECF0)    // 생리 직전 구간(더 연한 모브 틴트)
    val Amber = Color(0xFFC79A3C)       // 증상 표시(달력 칸 기호)

    /**
     * 구성원 아바타·타일 색(정체성 색). 화면 강조색(클레이)과 섞지 않고, 구성원 구분에만 쓴다.
     * OLO 계열의 뜻색(폴더 클레이·책 틸·문서 슬레이트·그 밖 웜그레이)에서 골랐다.
     */
    val ProfilePalette = listOf(
        Color(0xFFB95B3B), // 클레이
        Color(0xFF3E7F80), // 틸
        Color(0xFF55606B), // 슬레이트
        Color(0xFF7A7168), // 웜그레이
        Color(0xFFC79A3C), // 앰버
        Color(0xFF8E5B7A), // 모브
    )
}

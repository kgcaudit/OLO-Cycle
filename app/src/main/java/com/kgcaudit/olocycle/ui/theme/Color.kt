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

    // 주기 단계색 — 뜻이 있는 색. "따뜻함(생리) vs 차가움(가임·배란)" 2극 + 중립으로 축소(한 곳에 정의).
    // 배란은 별색(슬레이트)을 폐지하고 가임기 틸의 진한 명도로 통합. PMS는 생리에 가까운 옅은 로즈로.
    val Period = Color(0xFFC15B57)      // 웜 로즈(마젠타 기미 제거 → 클레이와 조화)
    val PeriodLight = Color(0xFFF3DFDB) // 예정일 채움(로즈의 옅은 톤)
    val Fertile = Color(0xFF5F8F86)     // 세이지 틸
    val FertileLight = Color(0xFFDCE7E2)
    val Ovulation = Color(0xFF3F6E67)   // 가임기 틸의 진한 명도(별색 아님)
    val OvulationLight = Color(0xFFCFE0DA)
    val Pms = Color(0xFFC88A86)         // 생리 직전(옅은 로즈)
    val PmsLight = Color(0xFFF7ECEA)
    val Amber = Color(0xFFC79A3C)       // 증상 표시(달력 칸 기호)

    /**
     * 구성원 아바타·타일 색(정체성 색). B안: 활성 구성원색이 그 순간 화면 강조색이 되므로,
     * 팔레트는 따뜻/중립 톤으로만 구성해 주기 의미색(생리 로즈·가임 틸)과 절대 겹치지 않게 한다.
     */
    val ProfilePalette = listOf(
        Color(0xFFB95B3B), // 클레이
        Color(0xFF5B6E86), // 더스티 블루(기존 틸→가임기색과 충돌하여 교체)
        Color(0xFF55606B), // 슬레이트
        Color(0xFF7A7168), // 웜그레이
        Color(0xFF9A7B45), // 브론즈
        Color(0xFF8E5B7A), // 모브
    )
}

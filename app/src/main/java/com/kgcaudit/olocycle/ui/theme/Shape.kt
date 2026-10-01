package com.kgcaudit.olocycle.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * OLO 계열 공용 버튼 모서리 = small(10.dp). Material3 Button 류는 shape 를 테마에서 읽지 않고
 * 알약(CornerFull)을 하드코딩하므로, 각 버튼에 이 값을 shape 로 넘겨 계열을 맞춘다.
 * (FAB·IconButton 은 예외 — 규칙상 알약 유지.) 값 변경은 OLO-Design 에 요청한다.
 */
val OloButtonShape = RoundedCornerShape(10.dp)

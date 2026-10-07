package com.kgcaudit.olocycle


import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgcaudit.olocycle.auth.BiometricAuth
import com.kgcaudit.olocycle.cycle.Phase
import com.kgcaudit.olocycle.data.BackupCodec
import com.kgcaudit.olocycle.data.Profile
import com.kgcaudit.olocycle.data.ProfilePhotos
import com.kgcaudit.olocycle.ui.OloIcons
import com.kgcaudit.olocycle.ui.theme.OloButtonShape
import com.kgcaudit.olocycle.ui.theme.OloColors
import com.kgcaudit.olocycle.ui.theme.OloTheme
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch


// ---------------------------------------------------------------------------- analysis tab (기록+통계 통합)

/** 분석 = 요약 KPI · 증상 인사이트 · 주기 추이 · 다음 예정 · 주기 히스토리 · 기록한 날. */
@Composable
internal fun AnalysisTab(state: HomeState, profileColor: Color, expanded: Boolean, onOpenDay: (LocalDate) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        // 화면명·프로필 리포트 부제는 상단 헤더(화면명 + 보조 맥락줄)가 보여 주므로 본문 중복은 뺀다.
        // 헤더와 첫 박스 사이 여백은 '오늘' 화면(HomeDashboard)과 동일하게 맞춘다.
        Spacer(Modifier.height(14.dp))

        // 요약 KPI(전폭) + 안내
        AnalysisKpiRow(state, profileColor)
        if (!state.isPersonalized) {
            Text("생리 시작을 3회 이상 기록하면 개인 평균으로 예측이 정확해집니다.",
                Modifier.padding(4.dp, 8.dp), color = OloColors.Muted, fontSize = 12.sp)
        }

        if (expanded) {
            // 태블릿: KPI 아래를 [증상·추이·다음예정 | 히스토리·기록목록] 2열로 나눈다.
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AnalysisSymptomCard(state, profileColor)
                    AnalysisTrendCard(state, profileColor)
                    AnalysisNextCard(state, profileColor)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AnalysisHistoryCard(state)
                    AnalysisLoggedDays(state, onOpenDay)
                }
            }
        } else {
            AnalysisSymptomCard(state, profileColor, topSpace = true)
            AnalysisTrendCard(state, profileColor, topSpace = true)
            AnalysisNextCard(state, profileColor, topSpace = true)
            Spacer(Modifier.height(14.dp))
            AnalysisHistoryCard(state)
            Spacer(Modifier.height(14.dp))
            AnalysisLoggedDays(state, onOpenDay)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
internal fun AnalysisKpiRow(state: HomeState, profileColor: Color) {
    val cycle = state.params?.cycleLength
    val period = state.params?.periodLength
    val regularity = when {
        state.recentCycleLengths.size < 2 -> "기록 부족"
        (state.recentCycleLengths.max() - state.recentCycleLengths.min()) <= 3 -> "규칙적"
        else -> "불규칙"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Kpi(Modifier.weight(1f), cycle?.let { "${it}일" } ?: "-", "평균 주기", profileColor)
        Kpi(Modifier.weight(1f), period?.let { "${it}일" } ?: "-", "평균 생리", profileColor)
        Kpi(Modifier.weight(1f), regularity, "규칙성", profileColor)
    }
}

/** 증상 인사이트 — 자주/반복/예측. 기록이 없으면 아무것도 그리지 않는다. */
@Composable
internal fun AnalysisSymptomCard(state: HomeState, profileColor: Color, topSpace: Boolean = false) {
    val symptoms = state.symptomStats()
    if (symptoms.isEmpty()) return
    if (topSpace) Spacer(Modifier.height(14.dp))
    AccentCard(profileColor) {
        Text("증상 인사이트", color = OloColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("자주 나타나는 증상", color = OloColors.Ink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        symptoms.take(4).forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, Modifier.width(60.dp), color = OloColors.Ink, fontSize = 12.5.sp, maxLines = 1)
                Box(Modifier.weight(1f).height(9.dp).clip(RoundedCornerShape(5.dp)).background(OloColors.SurfaceSoft)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(s.percent / 100f).clip(RoundedCornerShape(5.dp)).background(OloColors.Amber))
                }
                Spacer(Modifier.width(8.dp))
                Text("${s.percent}%", color = OloColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        val cycles = state.recordedCycleCount()
        val recurring = symptoms.filter { it.cyclesSeen >= minOf(3, maxOf(2, cycles)) }
        if (recurring.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRowChips(
                recurring.take(3).map { "반복 · ${it.name}(${it.cyclesSeen}주기)" } +
                    recurring.take(2).map { "예측 · 다음 주기 ${it.name} 가능" },
                profileColor,
            )
        }
    }
}

/** 주기 추이 차트. 기록이 없으면 그리지 않는다. */
@Composable
internal fun AnalysisTrendCard(state: HomeState, profileColor: Color, topSpace: Boolean = false) {
    if (state.recentCycleLengths.isEmpty()) return
    if (topSpace) Spacer(Modifier.height(14.dp))
    CycleTrendChart(state.recentCycleLengths, profileColor)
}

/** 다음 생리 예정 카드. 예측이 없으면 그리지 않는다. */
@Composable
internal fun AnalysisNextCard(state: HomeState, profileColor: Color, topSpace: Boolean = false) {
    val pred = state.prediction ?: return
    if (topSpace) Spacer(Modifier.height(14.dp))
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = profileColor.copy(alpha = 0.10f))) {
        Column(Modifier.padding(16.dp)) {
            Text("다음 생리 예정", color = OloColors.Muted, fontSize = 12.sp)
            Text("${pred.nextPeriodStart.monthValue}월 ${pred.nextPeriodStart.dayOfMonth}일" +
                (state.daysUntilNextPeriod?.let { d -> " (D-$d)" } ?: ""),
                color = OloColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text("배란 예정 ${pred.ovulation.monthValue}/${pred.ovulation.dayOfMonth} · 가임기 ${pred.fertileStart.monthValue}/${pred.fertileStart.dayOfMonth}~${pred.fertileEnd.dayOfMonth}",
                color = OloColors.Muted, fontSize = 12.sp)
        }
    }
}

/** 주기 히스토리 표. 없으면 그리지 않는다. */
@Composable
internal fun AnalysisHistoryCard(state: HomeState) {
    val history = state.periodHistory()
    if (history.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text("주기 히스토리", Modifier.padding(4.dp, 0.dp, 4.dp, 6.dp), color = OloColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, OloColors.Line, RoundedCornerShape(12.dp))) {
            Row(Modifier.fillMaxWidth().background(OloColors.SurfaceSoft).padding(14.dp, 9.dp)) {
                Text("생리 구간 (기간)", Modifier.weight(1f), color = OloColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("주기", color = OloColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            history.take(12).forEach { h ->
                val days = (h.end.toEpochDay() - h.start.toEpochDay() + 1).toInt()
                Row(Modifier.fillMaxWidth().padding(14.dp, 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("%02d.%02d ~ %02d.%02d (%d일)".format(h.start.monthValue, h.start.dayOfMonth, h.end.monthValue, h.end.dayOfMonth, days),
                        Modifier.weight(1f), color = OloColors.Ink, fontSize = 13.sp)
                    Text(h.cycleLength?.let { "${it}일" } ?: "—", color = OloColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = OloColors.Line)
            }
        }
    }
}

/** 기록한 날 목록. */
@Composable
internal fun AnalysisLoggedDays(state: HomeState, onOpenDay: (LocalDate) -> Unit) {
    val days = state.loggedDays()
    Column(Modifier.fillMaxWidth()) {
        Text("기록한 날 · ${days.size}일", Modifier.padding(4.dp, 0.dp, 4.dp, 6.dp), color = OloColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (days.isEmpty()) {
            Text("아래 ＋ 버튼이나 달력 날짜를 눌러 기록을 남겨 보세요.", Modifier.padding(4.dp, 4.dp), color = OloColors.Muted, fontSize = 13.sp)
        } else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, OloColors.Line, RoundedCornerShape(12.dp))) {
                days.take(30).forEachIndexed { i, rec ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenDay(rec.date) }.padding(14.dp, 11.dp)) {
                        Column {
                            Text("${rec.date.monthValue}월 ${rec.date.dayOfMonth}일", fontWeight = FontWeight.Bold, color = OloColors.Ink, fontSize = 13.5.sp)
                            Text(recordSummary(rec), color = OloColors.Muted, fontSize = 12.5.sp)
                        }
                    }
                    if (i < days.take(30).size - 1) HorizontalDivider(color = OloColors.Line)
                }
            }
        }
    }
}

/**
 * 최근 주기 길이 추이 차트. 기준선 + 평균 점선 + 값 라벨 + 얇은 막대. 막대는 밑변 0 대신 (최소값−2)를
 * 바닥으로 스케일해 며칠 차이도 눈에 띄게 한다. 구상안·대조가 같은 부품을 쓰도록 분리.
 */
@Composable
internal fun CycleTrendChart(lengths: List<Int>, accent: Color) {
    AccentCard(accent) {
        Text("최근 주기 길이(일)", color = OloColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        val maxLen = lengths.max()
        val minScale = (lengths.min() - 2).coerceAtLeast(1)
        val denom = (maxLen - minScale).toFloat().coerceAtLeast(1f)
        val avg = lengths.average().toFloat()
        Box(Modifier.fillMaxWidth().height(120.dp)) {
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val labelH = 16.dp.toPx(); val chartH = size.height - labelH
                drawLine(OloColors.Line, Offset(0f, chartH), Offset(size.width, chartH), 1.5.dp.toPx())
                val avgY = chartH - ((avg - minScale) / denom).coerceIn(0f, 1f) * (chartH - 8.dp.toPx())
                drawLine(OloColors.Muted.copy(alpha = 0.5f), Offset(0f, avgY), Offset(size.width, avgY),
                    1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            }
            Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                lengths.forEach { len ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$len", fontSize = 10.sp, color = OloColors.Muted, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        val frac = ((len - minScale) / denom).coerceIn(0.08f, 1f)
                        Box(Modifier.width(18.dp).height((84 * frac).dp.coerceAtLeast(8.dp))
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).background(accent))
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
        Text("점선 = 평균 ${avg.toInt()}일", color = OloColors.Muted, fontSize = 10.sp)
    }
}

/** 작은 칩 묶음(반복·예측 등). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlowRowChips(labels: List<String>, accent: Color) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { t ->
            Text(t, Modifier.clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.12f)).padding(9.dp, 4.dp),
                color = OloColors.Ink, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

internal fun recordSummary(rec: com.kgcaudit.olocycle.data.DayRecord): String {
    val parts = buildList {
        rec.flow?.let { add("생리량 " + listOf("없음", "적음", "보통", "많음").getOrElse(it) { "-" }) }
        rec.symptoms?.takeIf { it.isNotBlank() }?.let { add(it) }
        rec.mood?.let { add(it) }
        rec.temperature?.let { add("${it}℃") }
        rec.memo?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return parts.joinToString(" · ").ifEmpty { "기록 열기" }
}

@Composable
internal fun Kpi(modifier: Modifier, value: String, label: String, accent: Color) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(OloColors.SurfaceSoft).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = accent, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = OloColors.Muted, fontSize = 11.sp)
    }
}

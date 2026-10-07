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


// ---------------------------------------------------------------------------- calendar tab

internal enum class CalMode { MONTH, YEAR }

/** 달력 탭: 월/연 전환. 월=큰 달력(칸 정보·스와이프·연월 피커), 연=12개월 패턴 한눈 보기. */
@Composable
internal fun CalendarTab(
    state: HomeState, profileColor: Color, visibleMonth: YearMonth, expanded: Boolean,
    onSetMonth: (YearMonth) -> Unit, onDayClick: (LocalDate) -> Unit,
    onSetPeriodStart: (LocalDate, Boolean) -> Unit, onSetPeriodEnd: (LocalDate, Boolean) -> Unit,
    onSaveRecord: (LocalDate, Int?, List<String>, String?, Double?, Double?, String?) -> Unit,
) {
    var mode by remember { mutableStateOf(CalMode.MONTH) }
    var showPicker by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(state.today) } // 태블릿 오른쪽 패널이 보여 줄 날
    val density = LocalDensity.current
    val gridOffset = remember { Animatable(0f) }
    var lastMonth by remember { mutableStateOf(visibleMonth) }
    LaunchedEffect(visibleMonth) {
        if (visibleMonth != lastMonth) {
            val dir = if (visibleMonth.isAfter(lastMonth)) 1 else -1
            lastMonth = visibleMonth
            gridOffset.snapTo(with(density) { 64.dp.toPx() } * dir)
            gridOffset.animateTo(0f, tween(260))
        }
    }

    // 달력 본체(월/연). 날짜를 누르면: 휴대폰=다이얼로그, 태블릿=오른쪽 패널 갱신.
    @Composable
    fun CalendarBody(modifier: Modifier, dayClick: (LocalDate) -> Unit) {
        Column(modifier) {
            if (mode == CalMode.MONTH) {
                MonthHeader(
                    visibleMonth, profileColor,
                    mode = mode, onSetMode = { mode = it },
                    onPrev = { onSetMonth(visibleMonth.minusMonths(1)) },
                    onNext = { onSetMonth(visibleMonth.plusMonths(1)) },
                    onTitleClick = { showPicker = true },
                    onToday = { onSetMonth(YearMonth.now()) },
                )
                // 날짜 칸은 정사각형으로(인위적 세로 늘림 없음). 남는 세로 공간은 아래에 두고 셀을 늘리지 않는다.
                Box(
                    Modifier.fillMaxWidth().pointerInput(visibleMonth) {
                        val threshold = 56.dp.toPx()
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total <= -threshold) onSetMonth(visibleMonth.plusMonths(1))
                                else if (total >= threshold) onSetMonth(visibleMonth.minusMonths(1))
                            },
                        ) { change, dragAmount -> total += dragAmount; change.consume() }
                    },
                ) {
                    Box(Modifier.fillMaxWidth().graphicsLayer { translationX = gridOffset.value }) {
                        MonthCalendar(visibleMonth, state.today, profileColor, state::phaseOf, state::recordOf,
                            state::calendarCellLabel, enabled = state.selected != null, onDayClick = dayClick, fillHeight = false)
                    }
                }
                Box(Modifier.padding(bottom = 8.dp)) { PhaseLegend() }
                Spacer(Modifier.weight(1f))
            } else {
                YearView(
                    year = visibleMonth.year, today = state.today, profileColor = profileColor,
                    phaseOf = state::phaseOf,
                    mode = mode, onSetMode = { mode = it },
                    onPrevYear = { onSetMonth(visibleMonth.minusYears(1)) },
                    onNextYear = { onSetMonth(visibleMonth.plusYears(1)) },
                    onToday = { onSetMonth(YearMonth.now()) },
                    onPickMonth = { m -> onSetMonth(YearMonth.of(visibleMonth.year, m)); mode = CalMode.MONTH },
                )
            }
        }
    }

    if (expanded) {
        // 태블릿: [달력 | 선택한 날 상세] 마스터-디테일. 날짜를 누르면 오른쪽에서 바로 편집.
        Row(Modifier.fillMaxSize()) {
            CalendarBody(Modifier.weight(1.5f).fillMaxHeight()) { selectedDay = it }
            VerticalDivider(color = OloColors.Line)
            // 기록 패널도 세로로 늘이지 않고 내용 높이에 맞춘다(상단 정렬, 넘치면 스크롤).
            DayDetailPanel(
                modifier = Modifier.weight(1f).padding(12.dp),
                date = selectedDay,
                isPeriodStart = selectedDay?.let { state.isPeriodStart(it) } ?: false,
                isPeriodEnd = selectedDay?.let { state.isRecordedPeriodEnd(it) } ?: false,
                existing = selectedDay?.let { state.recordOf(it) },
                onSave = { d, ps, pe, flow, sym, mood, temp, weight, memo ->
                    onSetPeriodStart(d, ps); onSetPeriodEnd(d, pe); onSaveRecord(d, flow, sym, mood, temp, weight, memo)
                },
            )
        }
    } else {
        CalendarBody(Modifier.fillMaxSize(), onDayClick)
    }

    if (showPicker) {
        MonthYearPickerDialog(visibleMonth, profileColor, state.today,
            onPick = { onSetMonth(it); showPicker = false },
            onDismiss = { showPicker = false })
    }
}

/** 작은 세그먼트 토글(월/연 등). */
@Composable
internal fun SegToggle(items: List<String>, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(9.dp)).background(OloColors.SurfaceSoft).padding(2.dp)) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(7.dp)).background(if (on) accent else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 16.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) Color.White else OloColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** 연(年) 달력: 12개월을 3열로 채워 생리/예측/가임/배란 패턴을 한눈에. 월을 누르면 그 달의 월간 달력으로. */
@Composable
internal fun YearView(
    year: Int, today: LocalDate, profileColor: Color, phaseOf: (LocalDate) -> Phase,
    mode: CalMode, onSetMode: (CalMode) -> Unit,
    onPrevYear: () -> Unit, onNextYear: () -> Unit, onToday: () -> Unit, onPickMonth: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // 월 헤더와 같은 구성: ‹ 2026년 ›  …  올해  [월|연].
        Row(Modifier.fillMaxWidth().padding(8.dp, 6.dp, 12.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onPrevYear, Modifier.size(34.dp)) { Icon(Icons.Default.ChevronLeft, "이전 해", tint = OloColors.Muted, modifier = Modifier.size(22.dp)) }
            Text("${year}년", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = profileColor, modifier = Modifier.padding(horizontal = 6.dp))
            IconButton(onNextYear, Modifier.size(34.dp)) { Icon(Icons.Default.ChevronRight, "다음 해", tint = OloColors.Muted, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToday, shape = OloButtonShape, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("올해", color = OloColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
            SegToggle(listOf("월", "연"), if (mode == CalMode.MONTH) 0 else 1, profileColor) {
                onSetMode(if (it == 0) CalMode.MONTH else CalMode.YEAR)
            }
        }
        // 미니 달력을 세로로 늘이지 않고 정사각 칸의 자연 높이로 둔다. 넘치면 스크롤(스트레치 대신).
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            (0..3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    (1..3).forEach { col ->
                        val m = row * 3 + col
                        Box(Modifier.weight(1f).padding(5.dp)) {
                            MiniMonth(YearMonth.of(year, m), today, profileColor, phaseOf) { onPickMonth(m) }
                        }
                    }
                }
            }
        }
        // 연 달력은 미니 칸이 작아 배란을 고리 대신 채움색으로 쓰므로, 범례도 채움색으로 맞춘다.
        Box(Modifier.padding(bottom = 8.dp)) { YearLegend() }
    }
}

/** 연 달력용 범례 — 미니 칸의 실제 채움색과 1:1로 맞춘다(배란도 채움색). */
@Composable
internal fun YearLegend() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendItem("생리", OloColors.Period)
        LegendItem("예정", OloColors.PeriodLight)
        LegendItem("가임기", OloColors.FertileLight)
        LegendItem("배란", OloColors.Ovulation)
    }
}

/** 연 달력의 한 달 미니 그리드. */
@Composable
internal fun MiniMonth(month: YearMonth, today: LocalDate, profileColor: Color, phaseOf: (LocalDate) -> Phase, onClick: () -> Unit) {
    val leading = month.atDay(1).dayOfWeek.value % 7
    val days = (0 until leading).map<Int, LocalDate?> { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    // 6주(42칸)로 채워 모든 달의 높이를 같게 → 연 뷰 격자가 세로로 늘지 않고 가지런하다.
    val cells = days + List(42 - days.size) { null }
    Column(
        // 세로로 늘이지 않는다: 미니 칸을 정사각형으로 두고, 미니 달력은 내용 높이에 맞춘다.
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(OloColors.Surface)
            .border(1.dp, OloColors.Line, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(6.dp, 5.dp),
    ) {
        Text("${month.monthValue}월", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = profileColor)
        Spacer(Modifier.height(3.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                    week.forEach { day ->
                        // 오늘은 구성원색 solid 칸으로(연 뷰에선 숫자가 없으므로 꽉 채워 또렷하게).
                        val c = when {
                            day == null -> Color.Transparent
                            day == today -> profileColor
                            else -> miniColor(phaseOf(day))
                        }
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(2.dp)).background(c))
                    }
                }
            }
        }
    }
}

/** 연 달력용 단계별 채움색(작은 칸이라 진한 원색 대신 톤을 통일). */
internal fun miniColor(phase: Phase): Color = when (phase) {
    Phase.PERIOD -> OloColors.Period
    Phase.PREDICTED_PERIOD -> OloColors.PeriodLight
    Phase.FERTILE -> OloColors.FertileLight
    Phase.OVULATION -> OloColors.Ovulation
    else -> Color(0x11000000)
}

/** 연·월 직접 선택: 연도 스테퍼 + 12개월 그리드. 선택 월·이번 달을 강조. */
@Composable
internal fun MonthYearPickerDialog(
    month: YearMonth, accent: Color, today: LocalDate,
    onPick: (YearMonth) -> Unit, onDismiss: () -> Unit,
) {
    var year by remember { mutableIntStateOf(month.year) }
    val thisMonth = YearMonth.from(today)
    OloDialog(
        title = "연·월 선택",
        onDismiss = onDismiss,
        confirmLabel = "닫기",
        onConfirm = onDismiss,
        dismissLabel = null,
        accent = accent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ year-- }) { Icon(Icons.Default.ChevronLeft, "이전 해", tint = OloColors.Muted) }
            Text("${year}년", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = OloColors.Ink)
            IconButton({ year++ }) { Icon(Icons.Default.ChevronRight, "다음 해", tint = OloColors.Muted) }
        }
        Spacer(Modifier.height(8.dp))
        (0..2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..4).forEach { col ->
                            val m = row * 4 + col
                            val ym = YearMonth.of(year, m)
                            val selected = ym == month
                            val isThis = ym == thisMonth
                            Box(
                                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) accent else OloColors.SurfaceSoft)
                                    .then(if (isThis && !selected) Modifier.border(1.5.dp, accent, RoundedCornerShape(12.dp)) else Modifier)
                                    .clickable { onPick(ym) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${m}월", fontSize = 14.sp,
                                    fontWeight = if (selected || isThis) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else OloColors.Ink)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
        TextButton(onClick = { onPick(thisMonth) }, shape = OloButtonShape, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("이번 달로", color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

internal fun phaseName(p: Phase): String = when (p) {
    Phase.PERIOD -> "생리기"
    Phase.PREDICTED_PERIOD -> "생리 예정"
    Phase.FOLLICULAR -> "난포기"
    Phase.FERTILE -> "가임기"
    Phase.OVULATION -> "배란기"
    Phase.LUTEAL -> "황체기"
    Phase.PMS -> "황체기" // 생리 직전 며칠(색상은 유지하되 별도 문구 없이 황체기로 표기)
    Phase.UNKNOWN -> "기록 없음"
}

@Composable
internal fun MonthHeader(
    month: YearMonth, profileColor: Color,
    mode: CalMode, onSetMode: (CalMode) -> Unit,
    onPrev: () -> Unit, onNext: () -> Unit, onTitleClick: () -> Unit, onToday: () -> Unit,
) {
    // 한 줄: ‹ 2026년 9월 ▾ ›  …  오늘  [월|연]. 화살표가 제목을 감싸고, 월/연 토글이 우측에 들어온다.
    Row(Modifier.fillMaxWidth().padding(8.dp, 6.dp, 12.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onPrev, Modifier.size(34.dp)) { Icon(Icons.Default.ChevronLeft, "이전 달", tint = OloColors.Muted, modifier = Modifier.size(22.dp)) }
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onTitleClick).padding(6.dp, 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${month.year}년 ${month.monthValue}월", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = profileColor)
            Icon(Icons.Default.ArrowDropDown, "연·월 선택", tint = profileColor, modifier = Modifier.size(22.dp))
        }
        IconButton(onNext, Modifier.size(34.dp)) { Icon(Icons.Default.ChevronRight, "다음 달", tint = OloColors.Muted, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onToday, shape = OloButtonShape, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Text("오늘", color = OloColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(4.dp))
        SegToggle(listOf("월", "연"), if (mode == CalMode.MONTH) 0 else 1, profileColor) {
            onSetMode(if (it == 0) CalMode.MONTH else CalMode.YEAR)
        }
    }
}

@Composable
internal fun MonthCalendar(
    month: YearMonth, today: LocalDate, profileColor: Color,
    phaseOf: (LocalDate) -> Phase, recordOf: (LocalDate) -> com.kgcaudit.olocycle.data.DayRecord?,
    labelOf: (LocalDate) -> String?,
    enabled: Boolean, onDayClick: (LocalDate) -> Unit, fillHeight: Boolean = false,
) {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value % 7
    val cells = (0 until leading).map<Int, LocalDate?> { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(
        Modifier.then(if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .padding(horizontal = 14.dp, vertical = if (fillHeight) 4.dp else 0.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(OloColors.Surface)
            .border(1.dp, OloColors.Line, RoundedCornerShape(16.dp))
            .padding(8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            KO_DOW.forEachIndexed { i, w ->
                Text(w, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp,
                    color = if (i == 0) OloColors.Period else OloColors.Muted)
            }
        }
        Spacer(Modifier.height(4.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().then(if (fillHeight) Modifier.weight(1f) else Modifier)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).then(if (fillHeight) Modifier.fillMaxHeight() else Modifier).padding(3.dp)) {
                        if (day != null) DayCell(day, day == today, profileColor, phaseOf(day), recordOf(day), labelOf(day), enabled, onDayClick, fillHeight)
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun DayCell(
    day: LocalDate, isToday: Boolean, profileColor: Color, phase: Phase,
    record: com.kgcaudit.olocycle.data.DayRecord?, label: String?, enabled: Boolean, onClick: (LocalDate) -> Unit, fill: Boolean = false,
) {
    val (bg, fg) = phaseColors(phase)
    val shape = RoundedCornerShape(10.dp)
    Box(
        (if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(1f)).clip(shape).background(bg)
            // 예측 생리 = 점선 테두리(Apple·Flo 관습), 배란 = ◯ 고리. (오늘은 숫자 뒤 원으로 표시 → 아래 참조)
            .drawBehind {
                val stroke = 2.dp.toPx()
                when (phase) {
                    Phase.PREDICTED_PERIOD -> drawRoundRect(
                        OloColors.Period, style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                    )
                    Phase.OVULATION -> {
                        val r = size.minDimension * 0.30f
                        drawCircle(OloColors.Ovulation, r, style = Stroke(stroke))
                    }
                    else -> {}
                }
            }
            .clickable(enabled = enabled) { onClick(day) },
    ) {
        // 위: 날짜 + 짧은 라벨(생리 N일 / 예정 / 배란), 아래: 기록 점.
        // 오늘 = 숫자 뒤 구성원색 원(Google·Apple 관습). 칸 채움색·배란 고리와 무관하게 또렷하다.
        Column(Modifier.align(Alignment.TopCenter).padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TodayAwareDayNumber(day.dayOfMonth, isToday, profileColor, fg)
            if (label != null) {
                Text(label, color = fg.copy(alpha = 0.9f), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, lineHeight = 9.sp, maxLines = 1)
            }
        }
        DayMarkers(record, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
    }
}

/** 날짜 숫자. 오늘이면 구성원색 원 안에 흰 숫자로, 아니면 단계색(fg) 숫자로 그린다. */
@Composable
internal fun TodayAwareDayNumber(dayOfMonth: Int, isToday: Boolean, accent: Color, fg: Color) {
    if (isToday) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Text("$dayOfMonth", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 14.sp)
        }
    } else {
        Text("$dayOfMonth", color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp)
    }
}

/** 칸 하단 기호: 생리량(물방울)·증상(앰버 점)·기분(틸 점)·메모(그레이 점). 최대 4개. */
@Composable
internal fun DayMarkers(record: com.kgcaudit.olocycle.data.DayRecord?, modifier: Modifier) {
    if (record == null) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        record.flow?.takeIf { it > 0 }?.let { FlowDrop(it) }
        if (!record.symptoms.isNullOrBlank()) MarkerDot(OloColors.Amber)
        if (!record.mood.isNullOrBlank()) MarkerDot(OloColors.Fertile)
        if (!record.memo.isNullOrBlank()) MarkerDot(OloColors.Muted)
    }
}

@Composable
internal fun MarkerDot(color: Color) {
    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
}

/** 생리량 물방울. 진하기로 양(1·2·3)을 표현. */
@Composable
internal fun FlowDrop(intensity: Int) {
    val alpha = when (intensity) { 1 -> 0.5f; 2 -> 0.75f; else -> 1f }
    Canvas(Modifier.size(6.dp, 8.dp)) {
        val w = size.width; val h = size.height
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w / 2, 0f)
            cubicTo(w, h * 0.55f, w, h * 0.72f, w, h * 0.7f)
            // teardrop: rounded bottom via arc-ish cubic
            cubicTo(w, h, 0f, h, 0f, h * 0.7f)
            cubicTo(0f, h * 0.72f, 0f, h * 0.55f, w / 2, 0f)
            close()
        }
        drawPath(p, OloColors.Period.copy(alpha = alpha))
    }
}

internal fun phaseColors(phase: Phase): Pair<Color, Color> = when (phase) {
    Phase.PERIOD -> OloColors.Period to Color.White
    Phase.PREDICTED_PERIOD -> OloColors.PeriodLight to OloColors.Period
    Phase.FERTILE -> OloColors.FertileLight to OloColors.Fertile
    Phase.OVULATION -> OloColors.OvulationLight to OloColors.Ovulation
    Phase.PMS -> OloColors.PmsLight to OloColors.Pms
    else -> Color.White to OloColors.Ink
}

/** 범례 — 각 칸을 실제 달력 칸의 축소판으로 그려 달력 색과 정확히 일치시킨다. */
@Composable
internal fun PhaseLegend() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendItem("생리", OloColors.Period)                                   // 꽉 찬 로즈
        LegendItem("예정", OloColors.PeriodLight, dashBorder = OloColors.Period) // 연분홍 + 점선
        LegendItem("가임기", OloColors.FertileLight)                           // 연청록
        LegendItem("배란", OloColors.OvulationLight, ring = OloColors.Ovulation) // 연회색 + 고리
    }
}

/** 달력 칸과 같은 규칙(채움색·점선 테두리·배란 고리)으로 그린 범례 스와치. */
@Composable
internal fun LegendItem(label: String, fill: Color, dashBorder: Color? = null, ring: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(fill).drawBehind {
                dashBorder?.let {
                    drawRoundRect(it, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                }
                ring?.let { drawCircle(it, size.minDimension * 0.34f, style = Stroke(1.6.dp.toPx())) }
            },
        )
        Spacer(Modifier.width(5.dp)); Text(label, fontSize = 12.sp, color = OloColors.Muted)
    }
}

package com.kgcaudit.olocycle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgcaudit.olocycle.cycle.Phase
import com.kgcaudit.olocycle.data.Profile
import com.kgcaudit.olocycle.ui.theme.OloColors
import com.kgcaudit.olocycle.ui.theme.OloTheme
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OloTheme { App() } }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Default.Home),
    RECORD("기록", Icons.Default.Edit),
    STATS("통계", Icons.Default.BarChart),
    SETTINGS("설정", Icons.Default.Settings),
}

@Composable
private fun App(vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.HOME) }
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    var showAdd by remember { mutableStateOf(false) }
    var editProfile by remember { mutableStateOf<Profile?>(null) }
    var recordDate by remember { mutableStateOf<LocalDate?>(null) }
    var showAbout by remember { mutableStateOf(false) }

    // 화면 강조(버튼 등)는 클레이(역할색). 링·달력은 프로필 색(정체성)으로 물들여 "누구 달력"인지 보이게 한다.
    val profileColor = state.selected?.let { Color(it.color) } ?: OloColors.Primary

    Column(Modifier.fillMaxSize().background(OloColors.Background)) {
        HeaderBar(
            profiles = state.profiles,
            selectedId = state.selected?.id,
            onSelect = vm::select,
            onAdd = { showAdd = true },
            onEdit = { state.selected?.let { editProfile = it } },
        )
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.HOME -> HomeTab(state, profileColor, visibleMonth,
                    onPrevMonth = { visibleMonth = visibleMonth.minusMonths(1) },
                    onNextMonth = { visibleMonth = visibleMonth.plusMonths(1) },
                    onDayClick = { recordDate = it },
                    onLogToday = { recordDate = state.today })
                Tab.RECORD -> RecordTab(state) { recordDate = it }
                Tab.STATS -> StatsTab(state, profileColor)
                Tab.SETTINGS -> SettingsTab(state,
                    onToggleLock = { p, locked ->
                        vm.updateProfile(p, p.name, p.color, p.defaultCycleLength, p.defaultPeriodLength, p.onBirthControl, locked)
                    },
                    onEditProfile = { editProfile = it },
                    onAbout = { showAbout = true })
            }
        }
        BottomNav(tab) { tab = it }
    }

    if (showAdd) {
        ProfileEditorDialog(
            original = null,
            onDismiss = { showAdd = false },
            onSave = { name, color, cycle, period, birth, locked ->
                vm.addProfile(name, color, cycle, period, birth, locked); showAdd = false
            },
            onDelete = null,
        )
    }
    editProfile?.let { profile ->
        ProfileEditorDialog(
            original = profile,
            onDismiss = { editProfile = null },
            onSave = { name, color, cycle, period, birth, locked ->
                vm.updateProfile(profile, name, color, cycle, period, birth, locked); editProfile = null
            },
            onDelete = if (state.profiles.size > 1) { { vm.deleteProfile(profile); editProfile = null } } else null,
        )
    }
    recordDate?.let { date ->
        DayRecordDialog(
            date = date,
            isPeriodStart = state.isPeriodStart(date),
            existing = state.recordOf(date),
            onDismiss = { recordDate = null },
            onSetPeriodStart = { vm.setPeriodStart(date, it) },
            onSave = { flow, symptoms, mood, temp, memo ->
                vm.saveDayRecord(date, flow, symptoms, mood, temp, memo); recordDate = null
            },
        )
    }
    if (showAbout) AboutDialog { showAbout = false }
}

// ---------------------------------------------------------------------------- header + nav

@Composable
private fun HeaderBar(
    profiles: List<Profile>, selectedId: Long?,
    onSelect: (Long) -> Unit, onAdd: () -> Unit, onEdit: () -> Unit,
) {
    val gradient = Brush.horizontalGradient(listOf(lerp(OloColors.Primary, Color.Black, 0.06f), OloColors.Primary))
    Row(
        Modifier.fillMaxWidth().background(gradient).statusBarsPadding().padding(18.dp, 14.dp, 10.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("OLO Cycle", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        profiles.forEach { p ->
            Box(
                Modifier.padding(start = 8.dp).size(38.dp).clip(CircleShape).background(Color(p.color))
                    .border(2.dp, if (p.id == selectedId) Color.White else Color.White.copy(alpha = 0.45f), CircleShape)
                    .clickable { onSelect(p.id) },
                contentAlignment = Alignment.Center,
            ) {
                Text(p.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (p.locked) {
                    Box(Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(OloColors.Surface),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, "잠김", tint = OloColors.Muted, modifier = Modifier.size(9.dp))
                    }
                }
            }
        }
        Box(
            Modifier.padding(start = 8.dp).size(38.dp).clip(CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape).clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Add, "구성원 추가", tint = Color.White) }
        if (selectedId != null) {
            IconButton(onEdit) { Icon(Icons.Default.Edit, "프로필 편집", tint = Color.White) }
        }
    }
}

@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(OloColors.Surface).navigationBarsPadding()) {
        Tab.entries.forEach { t ->
            val on = t == current
            Column(
                Modifier.weight(1f).clickable { onSelect(t) }.padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, t.label, tint = if (on) OloColors.Primary else OloColors.Muted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(3.dp))
                Text(t.label, fontSize = 11.sp, color = if (on) OloColors.Primary else OloColors.Muted,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

// ---------------------------------------------------------------------------- home

@Composable
private fun HomeTab(
    state: HomeState, profileColor: Color, visibleMonth: YearMonth,
    onPrevMonth: () -> Unit, onNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit, onLogToday: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        CycleRing(state, profileColor)
        Button(
            onClick = onLogToday,
            enabled = state.selected != null,
            colors = ButtonDefaults.buttonColors(containerColor = OloColors.Primary),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 6.dp),
        ) { Text("＋ 오늘 기록", fontWeight = FontWeight.Bold) }

        MonthHeader(visibleMonth, profileColor, onPrevMonth, onNextMonth)
        MonthCalendar(visibleMonth, state.today, profileColor, state::phaseOf, state::recordOf,
            enabled = state.selected != null, onDayClick = onDayClick)
        PhaseLegend()
        Text("예측은 참고용 추정치이며 피임·진단의 근거가 아닙니다.",
            Modifier.fillMaxWidth().padding(16.dp), color = OloColors.Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

/** 원형 주기 링: 단계별 호 + 오늘 마커, 가운데에 단계·D-day. */
@Composable
private fun CycleRing(state: HomeState, profileColor: Color) {
    val cycle = state.params?.cycleLength ?: 28
    val periodLen = state.params?.periodLength ?: 5
    val pred = state.prediction
    Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(244.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 18.dp.toPx()
                val r = (size.minDimension - sw) / 2f
                val tl = Offset(center.x - r, center.y - r)
                val sz = Size(r * 2, r * 2)
                drawCircle(OloColors.Line.copy(alpha = 0.5f), r, style = Stroke(sw))
                fun arc(fromDay: Float, toDay: Float, color: Color) {
                    val start = -90f + fromDay / cycle * 360f
                    val sweep = (toDay - fromDay) / cycle * 360f
                    drawArc(color, start, sweep, useCenter = false, topLeft = tl, size = sz, style = Stroke(sw, cap = StrokeCap.Butt))
                }
                if (pred != null) {
                    val ps = pred.periodStart
                    arc(0f, periodLen.toFloat(), OloColors.Period)
                    val fs = ps.until(pred.fertileStart).days.toFloat()
                    val fe = ps.until(pred.fertileEnd).days.toFloat() + 1f
                    arc(fs.coerceIn(0f, cycle.toFloat()), fe.coerceIn(0f, cycle.toFloat()), OloColors.Fertile)
                    val ov = ps.until(pred.ovulation).days.toFloat()
                    arc(ov, ov + 1f, OloColors.Ovulation)
                    arc((cycle - 5).toFloat(), cycle.toFloat(), OloColors.Pms)
                }
                state.cycleDayIndex?.let { idx ->
                    val ang = Math.toRadians((-90f + idx.toFloat() / cycle * 360f).toDouble())
                    val mx = center.x + r * cos(ang).toFloat()
                    val my = center.y + r * sin(ang).toFloat()
                    drawCircle(Color.White, 11.dp.toPx(), Offset(mx, my))
                    drawCircle(profileColor, 11.dp.toPx(), Offset(mx, my), style = Stroke(4.dp.toPx()))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(phaseName(state.currentPhase()), color = profileColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                val d = state.daysUntilNextPeriod
                Text(
                    when { d == null -> "기록 전"; d >= 0 -> "D-$d"; else -> "D+${-d}" },
                    color = OloColors.Primary, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold,
                )
                Text(if (d == null) "생리 시작일을 기록" else "다음 생리까지", color = OloColors.Muted, fontSize = 13.sp)
                state.cycleDayIndex?.let { idx ->
                    val next = pred?.nextPeriodStart
                    Text("주기 ${idx}일째" + (next?.let { " · 예정 ${it.monthValue}/${it.dayOfMonth}" } ?: ""),
                        color = OloColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}

private fun phaseName(p: Phase): String = when (p) {
    Phase.PERIOD -> "생리기"
    Phase.PREDICTED_PERIOD -> "생리 예정"
    Phase.FOLLICULAR -> "난포기"
    Phase.FERTILE -> "가임기"
    Phase.OVULATION -> "배란기"
    Phase.LUTEAL -> "황체기"
    Phase.PMS -> "황체기 · PMS"
    Phase.UNKNOWN -> "기록 없음"
}

@Composable
private fun MonthHeader(month: YearMonth, profileColor: Color, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(20.dp, 4.dp, 12.dp, 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${month.year}년 ${month.monthValue}월", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = profileColor)
        Spacer(Modifier.weight(1f))
        IconButton(onPrev) { Icon(Icons.Default.ChevronLeft, "이전 달", tint = OloColors.Muted) }
        IconButton(onNext) { Icon(Icons.Default.ChevronRight, "다음 달", tint = OloColors.Muted) }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth, today: LocalDate, profileColor: Color,
    phaseOf: (LocalDate) -> Phase, recordOf: (LocalDate) -> com.kgcaudit.olocycle.data.DayRecord?,
    enabled: Boolean, onDayClick: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value % 7
    val cells = (0 until leading).map<Int, LocalDate?> { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(
        Modifier.padding(horizontal = 14.dp).clip(RoundedCornerShape(16.dp))
            .background(profileColor.copy(alpha = 0.05f))
            .border(2.dp, profileColor.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, w ->
                Text(w, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp,
                    color = if (i == 0) OloColors.Period else OloColors.Muted)
            }
        }
        Spacer(Modifier.height(4.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).padding(3.dp)) {
                        if (day != null) DayCell(day, day == today, profileColor, phaseOf(day), recordOf(day), enabled, onDayClick)
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate, isToday: Boolean, profileColor: Color, phase: Phase,
    record: com.kgcaudit.olocycle.data.DayRecord?, enabled: Boolean, onClick: (LocalDate) -> Unit,
) {
    val (bg, fg) = phaseColors(phase)
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(bg)
            // 예측 생리 = 점선 테두리(Apple·Flo 관습), 배란 = ◯ 고리, 오늘 = 프로필 색 실선.
            .drawBehind {
                val stroke = 2.dp.toPx()
                when (phase) {
                    Phase.PREDICTED_PERIOD -> drawRoundRect(
                        OloColors.Period, style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                    )
                    Phase.OVULATION -> {
                        val r = size.minDimension * 0.34f
                        drawCircle(OloColors.Ovulation, r, style = Stroke(stroke))
                    }
                    else -> {}
                }
                if (isToday) drawRoundRect(
                    profileColor, style = Stroke(stroke),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                )
            }
            .clickable(enabled = enabled) { onClick(day) },
        contentAlignment = Alignment.Center,
    ) {
        Text("${day.dayOfMonth}", color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center))
        DayMarkers(record, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
    }
}

/** 칸 하단 기호: 생리량(물방울)·증상(앰버 점)·기분(틸 점)·메모(그레이 점). 최대 4개. */
@Composable
private fun DayMarkers(record: com.kgcaudit.olocycle.data.DayRecord?, modifier: Modifier) {
    if (record == null) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        record.flow?.takeIf { it > 0 }?.let { FlowDrop(it) }
        if (!record.symptoms.isNullOrBlank()) MarkerDot(OloColors.Amber)
        if (!record.mood.isNullOrBlank()) MarkerDot(OloColors.Fertile)
        if (!record.memo.isNullOrBlank()) MarkerDot(OloColors.Muted)
    }
}

@Composable
private fun MarkerDot(color: Color) {
    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
}

/** 생리량 물방울. 진하기로 양(1·2·3)을 표현. */
@Composable
private fun FlowDrop(intensity: Int) {
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

private fun phaseColors(phase: Phase): Pair<Color, Color> = when (phase) {
    Phase.PERIOD -> OloColors.Period to Color.White
    Phase.PREDICTED_PERIOD -> OloColors.PeriodLight to OloColors.Period
    Phase.FERTILE -> OloColors.FertileLight to OloColors.Fertile
    Phase.OVULATION -> OloColors.OvulationLight to OloColors.Ovulation
    Phase.PMS -> OloColors.PmsLight to OloColors.Pms
    else -> Color.White to OloColors.Ink
}

@Composable
private fun PhaseLegend() {
    val items = listOf("생리" to OloColors.Period, "가임기" to OloColors.Fertile, "배란" to OloColors.Ovulation, "PMS" to OloColors.Pms)
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(color))
                Spacer(Modifier.width(5.dp)); Text(label, fontSize = 12.sp, color = OloColors.Muted)
            }
        }
    }
}

// ---------------------------------------------------------------------------- record tab

@Composable
private fun RecordTab(state: HomeState, onOpen: (LocalDate) -> Unit) {
    val days = state.loggedDays()
    Column(Modifier.fillMaxSize()) {
        SectionTitle("기록", "${state.selected?.name ?: ""} · ${days.size}일 기록됨")
        if (days.isEmpty()) {
            EmptyHint("홈의 ‘오늘 기록’이나 달력 날짜를 눌러 기록을 남겨 보세요.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(days.size) { i ->
                    val rec = days[i]
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(rec.date) }.padding(18.dp, 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("${rec.date.monthValue}월 ${rec.date.dayOfMonth}일", fontWeight = FontWeight.Bold, color = OloColors.Ink)
                            Text(recordSummary(rec), color = OloColors.Muted, fontSize = 13.sp)
                        }
                    }
                    HorizontalDivider(color = OloColors.Line)
                }
            }
        }
    }
}

private fun recordSummary(rec: com.kgcaudit.olocycle.data.DayRecord): String {
    val parts = buildList {
        rec.flow?.let { add("생리량 " + listOf("없음", "적음", "보통", "많음").getOrElse(it) { "-" }) }
        rec.symptoms?.takeIf { it.isNotBlank() }?.let { add(it) }
        rec.mood?.let { add(it) }
        rec.temperature?.let { add("${it}℃") }
        rec.memo?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return parts.joinToString(" · ").ifEmpty { "기록 열기" }
}

// ---------------------------------------------------------------------------- stats tab

@Composable
private fun StatsTab(state: HomeState, profileColor: Color) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("통계", state.selected?.name?.let { "$it · 최근 주기 분석" } ?: "")
        val cycle = state.params?.cycleLength
        val period = state.params?.periodLength
        val regularity = when {
            state.recentCycleLengths.size < 2 -> "기록 부족"
            (state.recentCycleLengths.max() - state.recentCycleLengths.min()) <= 3 -> "규칙적"
            else -> "불규칙"
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kpi(Modifier.weight(1f), cycle?.let { "${it}일" } ?: "-", "평균 주기")
            Kpi(Modifier.weight(1f), period?.let { "${it}일" } ?: "-", "평균 생리")
            Kpi(Modifier.weight(1f), regularity, "규칙성")
        }
        if (!state.isPersonalized) {
            Text("생리 시작을 3회 이상 기록하면 개인 평균으로 예측이 정확해집니다.",
                Modifier.padding(20.dp, 8.dp), color = OloColors.Muted, fontSize = 12.sp)
        }
        if (state.recentCycleLengths.isNotEmpty()) {
            Text("최근 주기 길이(일)", Modifier.padding(20.dp, 12.dp, 20.dp, 4.dp), color = OloColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            val maxLen = state.recentCycleLengths.max().coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.recentCycleLengths.forEach { len ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$len", fontSize = 11.sp, color = OloColors.Muted)
                        Box(Modifier.fillMaxWidth().height((70 * len / maxLen).dp.coerceAtLeast(6.dp))
                            .clip(RoundedCornerShape(6.dp)).background(profileColor))
                    }
                }
            }
        }
        state.prediction?.let {
            Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = OloColors.AccentContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("다음 생리 예정", color = OloColors.Muted, fontSize = 12.sp)
                    Text("${it.nextPeriodStart.monthValue}월 ${it.nextPeriodStart.dayOfMonth}일" +
                        (state.daysUntilNextPeriod?.let { d -> " (D-$d)" } ?: ""),
                        color = OloColors.OnAccentContainer, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("배란 예정 ${it.ovulation.monthValue}/${it.ovulation.dayOfMonth} · 가임기 ${it.fertileStart.monthValue}/${it.fertileStart.dayOfMonth}~${it.fertileEnd.dayOfMonth}",
                        color = OloColors.Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Kpi(modifier: Modifier, value: String, label: String) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(OloColors.SurfaceSoft).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = OloColors.Primary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = OloColors.Muted, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------------------- settings tab

@Composable
private fun SettingsTab(
    state: HomeState,
    onToggleLock: (Profile, Boolean) -> Unit,
    onEditProfile: (Profile) -> Unit,
    onAbout: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("설정 · 개인정보", state.selected?.name ?: "")
        // 프라이버시 안내를 최상단에.
        Card(Modifier.fillMaxWidth().padding(16.dp, 8.dp), colors = CardDefaults.cardColors(containerColor = OloColors.AccentContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text("내 데이터는 이 기기에만 있습니다", color = OloColors.OnAccentContainer, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                listOf("인터넷 권한 없음 · 서버 전송 없음", "추적·광고 SDK 없음", "계정 없이 사용").forEach {
                    Text("· $it", color = OloColors.OnAccentContainer, fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
        }
        state.selected?.let { sel ->
            Row(Modifier.fillMaxWidth().padding(20.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("이 프로필 잠금", fontWeight = FontWeight.SemiBold, color = OloColors.Ink)
                    Text("생체인증/PIN (표시 단계 · 실제 잠금은 다음 업데이트)", color = OloColors.Muted, fontSize = 12.sp)
                }
                Switch(sel.locked, { onToggleLock(sel, it) })
            }
            HorizontalDivider(color = OloColors.Line)
        }
        SettingRow("구성원 관리") {}
        state.profiles.forEach { p ->
            Row(Modifier.fillMaxWidth().clickable { onEditProfile(p) }.padding(24.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(p.color)), contentAlignment = Alignment.Center) {
                    Text(p.name.take(1), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(p.name, Modifier.weight(1f), color = OloColors.Ink)
                if (p.locked) Icon(Icons.Default.Lock, "잠김", tint = OloColors.Muted, modifier = Modifier.size(16.dp))
                Icon(Icons.Default.ChevronRight, "편집", tint = OloColors.Muted)
            }
        }
        HorizontalDivider(color = OloColors.Line)
        SettingRow("앱 정보 · 오픈소스 고지", onClick = onAbout)
    }
}

@Composable
private fun SettingRow(title: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(20.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = OloColors.Ink, fontWeight = FontWeight.SemiBold)
        if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = OloColors.Muted)
    }
}

// ---------------------------------------------------------------------------- shared bits

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 8.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = OloColors.Ink)
        if (subtitle.isNotBlank()) Text(subtitle, color = OloColors.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = OloColors.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
        title = { Text("OLO Cycle ${BuildConfig.VERSION_NAME}") },
        text = {
            Text(
                "가족 구성원별로 생리 주기를 따로 관리하는 앱입니다. 예측은 달력법 기반의 참고용 추정치이며 피임·진단의 근거가 아닙니다.\n\n" +
                    "· 데이터는 이 기기에만 저장 · 인터넷 권한 없음\n" +
                    "· 디자인: OLO 디자인 시스템 (Apache/MIT 오픈소스 기반)\n" +
                    "· 주기 카드 표시 기법 참고: ALL TV (MIT)",
                fontSize = 13.sp, lineHeight = 20.sp,
            )
        },
    )
}

// ---------------------------------------------------------------------------- dialogs (프로필 편집 · 일별 기록)

/**
 * Add or edit a member profile. [original] null means "add"; otherwise the fields prefill from it.
 * [onDelete] is shown only when deleting is allowed (never for the last remaining profile).
 */
@Composable
private fun ProfileEditorDialog(
    original: Profile?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Int, cycle: Int, period: Int, birthControl: Boolean, locked: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var colorIndex by remember {
        mutableStateOf(original?.let { p -> OloColors.ProfilePalette.indexOfFirst { it.toArgb() == p.color }.coerceAtLeast(0) } ?: 0)
    }
    var cycle by remember { mutableStateOf(original?.defaultCycleLength ?: 28) }
    var period by remember { mutableStateOf(original?.defaultPeriodLength ?: 5) }
    var birthControl by remember { mutableStateOf(original?.onBirthControl ?: false) }
    var locked by remember { mutableStateOf(original?.locked ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(name, OloColors.ProfilePalette[colorIndex].toArgb(), cycle, period, birthControl, locked) }) {
                Text(if (original == null) "만들기" else "저장")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        title = { Text(if (original == null) "구성원 추가" else "프로필 편집") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("이름(별명)") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Text("프로필 색상", fontSize = 12.sp, color = OloColors.Muted)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OloColors.ProfilePalette.forEachIndexed { i, c ->
                        Box(Modifier.size(30.dp).clip(CircleShape).background(c)
                            .then(if (i == colorIndex) Modifier.border(3.dp, OloColors.Ink, CircleShape) else Modifier)
                            .clickable { colorIndex = i })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Stepper("평균 주기(일)", cycle, 15, 60) { cycle = it }
                Stepper("평균 생리 기간(일)", period, 1, 10) { period = it }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("피임약 복용", Modifier.weight(1f), fontSize = 13.sp); Switch(birthControl, { birthControl = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("프로필 잠금(생체인증/PIN)", Modifier.weight(1f), fontSize = 13.sp); Switch(locked, { locked = it })
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    if (!confirmDelete) {
                        TextButton(onClick = { confirmDelete = true }) { Text("이 프로필 삭제", color = OloColors.Period) }
                    } else {
                        Text("이 구성원의 모든 기록이 함께 삭제됩니다.", color = OloColors.Period, fontSize = 12.sp)
                        Row {
                            TextButton(onClick = onDelete) { Text("삭제 확인", color = OloColors.Period) }
                            TextButton(onClick = { confirmDelete = false }) { Text("취소") }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp)
        IconButton({ if (value > min) onChange(value - 1) }) { Text("−", fontSize = 18.sp) }
        Text("$value", fontWeight = FontWeight.Bold)
        IconButton({ if (value < max) onChange(value + 1) }) { Text("+", fontSize = 18.sp) }
    }
}

private val FLOW_LABELS = listOf("없음", "적음", "보통", "많음")
private val SYMPTOM_OPTIONS = listOf("복통", "두통", "허리통증", "부종", "여드름", "피로", "메스꺼움", "유방통")
private val MOOD_OPTIONS = listOf("좋음", "평온", "예민", "우울", "불안")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayRecordDialog(
    date: LocalDate,
    isPeriodStart: Boolean,
    existing: com.kgcaudit.olocycle.data.DayRecord?,
    onDismiss: () -> Unit,
    onSetPeriodStart: (Boolean) -> Unit,
    onSave: (flow: Int?, symptoms: List<String>, mood: String?, temperature: Double?, memo: String?) -> Unit,
) {
    var periodStart by remember { mutableStateOf(isPeriodStart) }
    var flow by remember { mutableStateOf(existing?.flow) }
    val symptoms = remember {
        mutableStateListOf<String>().apply {
            existing?.symptoms?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { addAll(it) }
        }
    }
    var mood by remember { mutableStateOf(existing?.mood) }
    var temperature by remember { mutableStateOf(existing?.temperature?.toString() ?: "") }
    var memo by remember { mutableStateOf(existing?.memo ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onSetPeriodStart(periodStart)
                onSave(flow, symptoms.toList(), mood, temperature.toDoubleOrNull(), memo)
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        title = { Text("${date.monthValue}월 ${date.dayOfMonth}일 기록") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("생리 시작일", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Switch(periodStart, { periodStart = it })
                }
                Text("이 날을 생리 시작일로 지정하면 예측이 갱신됩니다.", color = OloColors.Muted, fontSize = 11.sp)

                FieldLabel("생리량", OloColors.Period)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FLOW_LABELS.forEachIndexed { i, label ->
                        SelectChip(label, selected = flow == i, color = OloColors.Period) { flow = if (flow == i) null else i }
                    }
                }
                FieldLabel("증상", OloColors.Amber)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    SYMPTOM_OPTIONS.forEach { s ->
                        SelectChip(s, selected = s in symptoms, color = OloColors.Pms) { if (s in symptoms) symptoms.remove(s) else symptoms.add(s) }
                    }
                }
                FieldLabel("기분", OloColors.Fertile)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MOOD_OPTIONS.forEach { m ->
                        SelectChip(m, selected = mood == m, color = OloColors.Fertile) { mood = if (mood == m) null else m }
                    }
                }
                FieldLabel("기초체온 (℃)")
                OutlinedTextField(temperature, { temperature = it }, singleLine = true, placeholder = { Text("예: 36.6") }, modifier = Modifier.fillMaxWidth())
                FieldLabel("메모", OloColors.Muted)
                OutlinedTextField(memo, { memo = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
    )
}

@Composable
private fun FieldLabel(text: String, dot: Color? = null) {
    Row(Modifier.padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(7.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = OloColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val bg = if (selected) color else OloColors.Surface
    val fg = if (selected) Color.White else OloColors.Ink
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, if (selected) color else OloColors.Line, RoundedCornerShape(16.dp))
            .background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

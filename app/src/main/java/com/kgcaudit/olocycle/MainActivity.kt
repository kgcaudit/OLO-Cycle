package com.kgcaudit.olocycle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OloTheme { HomeScreen() } }
    }
}

@Composable
private fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    var showAdd by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(OloColors.Background).statusBarsPadding(),
    ) {
        Header(state.selected)
        ProfileBar(
            profiles = state.profiles,
            selectedId = state.selected?.id,
            onSelect = vm::select,
            onAdd = { showAdd = true },
        )
        MonthHeader(visibleMonth, { visibleMonth = visibleMonth.minusMonths(1) }, { visibleMonth = visibleMonth.plusMonths(1) })
        MonthCalendar(
            month = visibleMonth,
            today = state.today,
            phaseOf = state::phaseOf,
            onDayClick = vm::togglePeriodStart,
        )
        Spacer(Modifier.height(12.dp))
        TodayCard(state.daysUntilNextPeriod, state.prediction?.nextPeriodStart, state.selected)
        Spacer(Modifier.weight(1f))
        DisclaimerBar()
    }

    if (showAdd) {
        AddProfileDialog(
            onDismiss = { showAdd = false },
            onCreate = { name, color, cycle, period, locked ->
                vm.addProfile(name, color, cycle, period, locked)
                showAdd = false
            },
        )
    }
}

@Composable
private fun Header(selected: Profile?) {
    Column(Modifier.fillMaxWidth().padding(20.dp, 14.dp, 20.dp, 8.dp)) {
        Text("OLO 사이클", color = OloColors.Primary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            selected?.let { "${it.name} 님의 주기" } ?: "구성원을 추가하세요",
            color = OloColors.Muted, fontSize = 14.sp,
        )
    }
}

@Composable
private fun ProfileBar(profiles: List<Profile>, selectedId: Long?, onSelect: (Long) -> Unit, onAdd: () -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(profiles, key = { it.id }) { p ->
            Avatar(p.name.take(1), Color(p.color), selected = p.id == selectedId) { onSelect(p.id) }
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onAdd)) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).border(2.dp, OloColors.Line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Add, "구성원 추가", tint = OloColors.Muted) }
                Spacer(Modifier.height(4.dp))
                Text("추가", fontSize = 12.sp, color = OloColors.Muted)
            }
        }
    }
}

@Composable
private fun Avatar(initial: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(color)
                .then(if (selected) Modifier.border(3.dp, OloColors.Primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        Spacer(Modifier.height(4.dp))
        Text(initial, fontSize = 12.sp, color = OloColors.Ink)
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(20.dp, 8.dp, 20.dp, 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${month.year}년 ${month.monthValue}월", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OloColors.Ink)
        Spacer(Modifier.weight(1f))
        IconButton(onPrev) { Icon(Icons.Default.ChevronLeft, "이전 달", tint = OloColors.Muted) }
        IconButton(onNext) { Icon(Icons.Default.ChevronRight, "다음 달", tint = OloColors.Muted) }
    }
}

@Composable
private fun MonthCalendar(month: YearMonth, today: LocalDate, phaseOf: (LocalDate) -> Phase, onDayClick: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value % 7 // Sunday-first grid
    val days = month.lengthOfMonth()
    val cells = (0 until leading).map<Int, LocalDate?> { null } + (1..days).map { month.atDay(it) }

    Column(Modifier.padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, w ->
                Text(
                    w, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp,
                    color = if (i == 0) OloColors.Period else OloColors.Muted,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day -> Box(Modifier.weight(1f).padding(3.dp)) { if (day != null) DayCell(day, day == today, phaseOf(day), onDayClick) } }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, isToday: Boolean, phase: Phase, onClick: (LocalDate) -> Unit) {
    val (bg, fg) = phaseColors(phase)
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(bg)
            .then(if (isToday) Modifier.border(2.dp, OloColors.Primary, RoundedCornerShape(10.dp)) else Modifier)
            .clickable { onClick(day) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${day.dayOfMonth}", color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            phaseLabel(phase)?.let { Text(it, color = fg, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

private fun phaseColors(phase: Phase): Pair<Color, Color> = when (phase) {
    Phase.PERIOD -> OloColors.Period to Color.White
    Phase.PREDICTED_PERIOD -> OloColors.PeriodLight to OloColors.Period
    Phase.FERTILE -> OloColors.FertileLight to OloColors.Fertile
    Phase.OVULATION -> OloColors.OvulationLight to OloColors.Ovulation
    Phase.PMS -> OloColors.PmsLight to OloColors.Pms
    else -> OloColors.SurfaceSoft to OloColors.Ink
}

private fun phaseLabel(phase: Phase): String? = when (phase) {
    Phase.OVULATION -> "배란"
    Phase.PMS -> "PMS"
    Phase.PREDICTED_PERIOD -> "예정"
    else -> null
}

@Composable
private fun TodayCard(daysUntil: Int?, nextStart: LocalDate?, selected: Profile?) {
    if (selected == null) return
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp), color = OloColors.PeriodLight,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("오늘 · ${LocalDate.now().monthValue}월 ${LocalDate.now().dayOfMonth}일", color = OloColors.Muted, fontSize = 12.sp)
            val headline = when {
                daysUntil == null -> "생리 시작일을 기록해 주세요"
                daysUntil > 0 -> "다음 생리까지 D-$daysUntil"
                daysUntil == 0 -> "오늘이 생리 예정일이에요"
                else -> "예정일에서 ${-daysUntil}일 지남"
            }
            Text(headline, color = OloColors.Period, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            nextStart?.let {
                Text("예정일 ${it.monthValue}/${it.dayOfMonth} · 날짜를 눌러 생리 시작일 기록", color = OloColors.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DisclaimerBar() {
    Text(
        "예측은 참고용 추정치이며 피임·진단의 근거가 아닙니다.",
        Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
        color = OloColors.Muted, fontSize = 11.sp, textAlign = TextAlign.Center,
    )
}

@Composable
private fun AddProfileDialog(onDismiss: () -> Unit, onCreate: (String, Int, Int, Int, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableStateOf(0) }
    var cycle by remember { mutableStateOf(28) }
    var period by remember { mutableStateOf(5) }
    var locked by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onCreate(name, OloColors.ProfilePalette[colorIndex].toArgb(), cycle, period, locked)
            }) { Text("만들기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        title = { Text("구성원 추가") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("이름(별명)") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Text("프로필 색상", fontSize = 12.sp, color = OloColors.Muted)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OloColors.ProfilePalette.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(c)
                                .then(if (i == colorIndex) Modifier.border(3.dp, OloColors.Ink, CircleShape) else Modifier)
                                .clickable { colorIndex = i },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Stepper("평균 주기(일)", cycle, 15, 60) { cycle = it }
                Stepper("평균 생리 기간(일)", period, 1, 10) { period = it }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("프로필 잠금(생체인증/PIN)", Modifier.weight(1f), fontSize = 13.sp)
                    Switch(locked, { locked = it })
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


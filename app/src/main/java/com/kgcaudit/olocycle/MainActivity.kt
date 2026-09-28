package com.kgcaudit.olocycle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
    var editProfile by remember { mutableStateOf<Profile?>(null) }
    var recordDate by remember { mutableStateOf<LocalDate?>(null) }

    // OLO 규칙: 화면 강조는 클레이 하나. 구성원은 아바타·타일 색으로만 구분한다
    // (화면 전체를 프로필 색으로 물들이지 않는다).
    val accent = OloColors.Primary

    Column(Modifier.fillMaxSize().background(OloColors.Background)) {
        BrandHeader(
            selected = state.selected,
            accent = accent,
            onEditSelected = { state.selected?.let { editProfile = it } },
        )
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
            accent = accent,
            phaseOf = state::phaseOf,
            hasNote = state::hasNote,
            enabled = state.selected != null,
            onDayClick = { recordDate = it },
        )
        PhaseLegend()
        Spacer(Modifier.height(8.dp))
        TodayCard(state.daysUntilNextPeriod, state.prediction?.nextPeriodStart, state.selected, accent)
        Spacer(Modifier.weight(1f))
        DisclaimerBar()
    }

    if (showAdd) {
        ProfileEditorDialog(
            original = null,
            onDismiss = { showAdd = false },
            onSave = { name, color, cycle, period, birth, locked ->
                vm.addProfile(name, color, cycle, period, birth, locked)
                showAdd = false
            },
            onDelete = null,
        )
    }

    editProfile?.let { profile ->
        ProfileEditorDialog(
            original = profile,
            onDismiss = { editProfile = null },
            onSave = { name, color, cycle, period, birth, locked ->
                vm.updateProfile(profile, name, color, cycle, period, birth, locked)
                editProfile = null
            },
            onDelete = if (state.profiles.size > 1) {
                { vm.deleteProfile(profile); editProfile = null }
            } else null,
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
                vm.saveDayRecord(date, flow, symptoms, mood, temp, memo)
                recordDate = null
            },
        )
    }
}

@Composable
private fun BrandHeader(selected: Profile?, accent: Color, onEditSelected: () -> Unit) {
    // Gradient runs from the member's color to a darker shade of it, giving each profile a
    // distinct header while keeping one visual language.
    val gradient = Brush.horizontalGradient(listOf(accent, lerp(accent, Color.Black, 0.28f)))
    Row(
        Modifier.fillMaxWidth().background(gradient).statusBarsPadding()
            .padding(20.dp, 16.dp, 12.dp, 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("OLO Cycle", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                selected?.let { "${it.name} 님의 주기" } ?: "구성원을 추가하세요",
                color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp,
            )
        }
        if (selected != null) {
            IconButton(onEditSelected) {
                Icon(Icons.Default.Edit, "프로필 편집", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ProfileBar(profiles: List<Profile>, selectedId: Long?, onSelect: (Long) -> Unit, onAdd: () -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(profiles, key = { it.id }) { p ->
            Avatar(p, selected = p.id == selectedId, onClick = { onSelect(p.id) })
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onAdd)) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).border(2.dp, OloColors.Line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Add, "구성원 추가", tint = OloColors.Muted) }
                Spacer(Modifier.height(5.dp))
                Text("추가", fontSize = 12.sp, color = OloColors.Muted)
            }
        }
    }
}

@Composable
private fun Avatar(profile: Profile, selected: Boolean, onClick: () -> Unit) {
    val ring = lerp(Color(profile.color), Color.Black, 0.35f) // darker shade of the member's own color
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(52.dp)
                    .then(if (selected) Modifier.border(3.dp, ring, CircleShape).padding(3.dp) else Modifier)
                    .clip(CircleShape).background(Color(profile.color)),
                contentAlignment = Alignment.Center,
            ) { Text(profile.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            if (profile.locked) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape)
                        .background(OloColors.Surface).border(1.dp, OloColors.Line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Lock, "잠김", tint = OloColors.Muted, modifier = Modifier.size(11.dp)) }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(profile.name, fontSize = 12.sp, color = if (selected) OloColors.Primary else OloColors.Ink,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun PhaseLegend() {
    val items = listOf(
        "생리" to OloColors.Period, "가임기" to OloColors.Fertile,
        "배란" to OloColors.Ovulation, "PMS" to OloColors.Pms,
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(color))
                Spacer(Modifier.width(5.dp))
                Text(label, fontSize = 12.sp, color = OloColors.Muted)
            }
        }
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
private fun MonthCalendar(
    month: YearMonth,
    today: LocalDate,
    accent: Color,
    phaseOf: (LocalDate) -> Phase,
    hasNote: (LocalDate) -> Boolean,
    enabled: Boolean,
    onDayClick: (LocalDate) -> Unit,
) {
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
                week.forEach { day ->
                    Box(Modifier.weight(1f).padding(3.dp)) {
                        if (day != null) DayCell(day, day == today, accent, phaseOf(day), hasNote(day), enabled, onDayClick)
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, isToday: Boolean, accent: Color, phase: Phase, hasNote: Boolean, enabled: Boolean, onClick: (LocalDate) -> Unit) {
    val (bg, fg) = phaseColors(phase)
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(bg)
            .then(if (isToday) Modifier.border(2.dp, accent, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(enabled = enabled) { onClick(day) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${day.dayOfMonth}", color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            phaseLabel(phase)?.let { Text(it, color = fg, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
        }
        if (hasNote) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                    .size(4.dp).clip(CircleShape).background(fg),
            )
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
private fun TodayCard(daysUntil: Int?, nextStart: LocalDate?, selected: Profile?, accent: Color) {
    if (selected == null) return
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp), color = OloColors.AccentContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("오늘 · ${LocalDate.now().monthValue}월 ${LocalDate.now().dayOfMonth}일", color = OloColors.Muted, fontSize = 12.sp)
            val headline = when {
                daysUntil == null -> "생리 시작일을 기록해 주세요"
                daysUntil > 0 -> "다음 생리까지 D-$daysUntil"
                daysUntil == 0 -> "오늘이 생리 예정일이에요"
                else -> "예정일에서 ${-daysUntil}일 지남"
            }
            Text(headline, color = OloColors.OnAccentContainer, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
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
        mutableStateOf(
            original?.let { p -> OloColors.ProfilePalette.indexOfFirst { it.toArgb() == p.color }.coerceAtLeast(0) } ?: 0,
        )
    }
    var cycle by remember { mutableStateOf(original?.defaultCycleLength ?: 28) }
    var period by remember { mutableStateOf(original?.defaultPeriodLength ?: 5) }
    var birthControl by remember { mutableStateOf(original?.onBirthControl ?: false) }
    var locked by remember { mutableStateOf(original?.locked ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onSave(name, OloColors.ProfilePalette[colorIndex].toArgb(), cycle, period, birthControl, locked)
            }) { Text(if (original == null) "만들기" else "저장") }
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
                    Text("피임약 복용", Modifier.weight(1f), fontSize = 13.sp)
                    Switch(birthControl, { birthControl = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("프로필 잠금(생체인증/PIN)", Modifier.weight(1f), fontSize = 13.sp)
                    Switch(locked, { locked = it })
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    if (!confirmDelete) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("이 프로필 삭제", color = OloColors.Period)
                        }
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


/** Flow-intensity labels; index maps to DayRecord.flow (0 = 없음). */
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

                FieldLabel("생리량")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FLOW_LABELS.forEachIndexed { i, label ->
                        SelectChip(label, selected = flow == i, color = OloColors.Period) {
                            flow = if (flow == i) null else i
                        }
                    }
                }

                FieldLabel("증상")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    SYMPTOM_OPTIONS.forEach { s ->
                        SelectChip(s, selected = s in symptoms, color = OloColors.Pms) {
                            if (s in symptoms) symptoms.remove(s) else symptoms.add(s)
                        }
                    }
                }

                FieldLabel("기분")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MOOD_OPTIONS.forEach { m ->
                        SelectChip(m, selected = mood == m, color = OloColors.Fertile) {
                            mood = if (mood == m) null else m
                        }
                    }
                }

                FieldLabel("기초체온 (℃)")
                OutlinedTextField(
                    temperature, { temperature = it }, singleLine = true,
                    placeholder = { Text("예: 36.6") },
                    modifier = Modifier.fillMaxWidth(),
                )

                FieldLabel("메모")
                OutlinedTextField(memo, { memo = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = OloColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun SelectChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val bg = if (selected) color else OloColors.Surface
    val fg = if (selected) Color.White else OloColors.Ink
    Box(
        Modifier.clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (selected) color else OloColors.Line, RoundedCornerShape(16.dp))
            .background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

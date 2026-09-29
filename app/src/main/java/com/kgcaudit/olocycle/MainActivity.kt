package com.kgcaudit.olocycle

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.ui.platform.LocalDensity
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
import com.kgcaudit.olocycle.data.Profile
import com.kgcaudit.olocycle.data.ProfilePhotos
import com.kgcaudit.olocycle.ui.theme.OloColors
import com.kgcaudit.olocycle.ui.theme.OloTheme
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideFromRecents()
        setContent { OloTheme { App() } }
    }

    /**
     * 최근 앱(작업 전환) 화면 미리보기에 생리 기록 내용이 남지 않게 가린다.
     * API 33+ 는 미리보기만 끄고 사용자의 직접 스크린샷은 그대로 허용(setRecentsScreenshotEnabled),
     * 그 이하는 FLAG_SECURE 로 미리보기·캡처를 함께 차단한다.
     */
    private fun hideFromRecents() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        } else {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Default.Home),
    CALENDAR("달력", Icons.Default.CalendarMonth),
    RECORD("기록", Icons.Default.Edit),
    STATS("통계", Icons.Default.BarChart),
}

@Composable
private fun App(vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val appLockEnabled by vm.appLockEnabled.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? FragmentActivity
    var tab by remember { mutableStateOf(Tab.HOME) }
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    var showAdd by remember { mutableStateOf(false) }
    var editProfile by remember { mutableStateOf<Profile?>(null) }
    var recordDate by remember { mutableStateOf<LocalDate?>(null) }
    var showAbout by remember { mutableStateOf(false) }

    // 앱 잠금(프로필 무관, 앱 전체 1개). 첫 구동엔 잠긴 상태로 시작하고, 홈/타 앱으로 나갔다 오면 다시 잠근다.
    var authed by rememberSaveable { mutableStateOf(false) }
    var authInProgress by remember { mutableStateOf(false) }
    val gated = appLockEnabled && !authed

    val requestUnlock: () -> Unit = req@{
        val act = activity ?: return@req
        if (authInProgress) return@req
        authInProgress = true
        BiometricAuth.authenticate(
            act, title = "OLO Cycle 잠금 해제", subtitle = "생체인증 또는 화면 잠금으로 확인",
            onSuccess = { authInProgress = false; authed = true },
            onError = { msg -> authInProgress = false; Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
        )
    }

    // 화면에서 사라지면(ON_STOP) 다시 잠그고, 앱이 실제로 앞으로 돌아왔을 때(ON_RESUME) 인증창을 자동으로 띄운다.
    // (백그라운드에서 띄우면 인증창이 뜨지 못해 수동 버튼만 남던 문제를 고침.)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                // 회전 등 구성 변경으로 인한 정지는 재잠금에서 제외(진짜 백그라운드로 나갈 때만 잠근다).
                Lifecycle.Event.ON_STOP ->
                    if (!authInProgress && activity?.isChangingConfigurations != true) authed = false
                Lifecycle.Event.ON_RESUME -> if (appLockEnabled && !authed && !authInProgress) requestUnlock()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    if (gated) {
        AppLockGate(onUnlock = requestUnlock)
        return
    }

    var showSettings by remember { mutableStateOf(false) }
    // 설정은 앱 전역 → 탭이 아니라 우상단 ⚙ 로 진입하는 전체화면. 구성원 스위처를 띄우지 않는다.
    if (showSettings) {
        SettingsScreen(
            appLockEnabled = appLockEnabled,
            onToggleAppLock = { on ->
                if (on && !BiometricAuth.canAuthenticate(context)) {
                    Toast.makeText(context, "기기 화면 잠금(생체/PIN)을 먼저 설정해 주세요.", Toast.LENGTH_LONG).show()
                } else {
                    vm.setAppLock(on)
                    if (on) authed = true
                }
            },
            onAbout = { showAbout = true },
            onBack = { showSettings = false },
        )
        if (showAbout) AboutDialog { showAbout = false }
        return
    }

    // 링·달력·강조는 프로필 색(정체성)으로 물들여 "누구 화면"인지 보이게 한다.
    val profileColor = state.selected?.let { Color(it.color) } ?: OloColors.Primary

    // 좌우 스와이프로 구성원 이동: 왼쪽으로 밀면 다음, 오른쪽으로 밀면 이전 구성원.
    val switchMember: (Int) -> Unit = step@{ dir ->
        val list = state.profiles
        if (list.size < 2) return@step
        val idx = list.indexOfFirst { it.id == state.selected?.id }.takeIf { it >= 0 } ?: return@step
        val next = (idx + dir).coerceIn(0, list.size - 1)
        if (next != idx) vm.select(list[next].id)
    }

    // 구성원이 바뀌면 콘텐츠를 이동 방향에서 슬라이드+페이드로 부드럽게 들어오게 한다(즉시 교체 방지).
    val density = LocalDensity.current
    val selId = state.selected?.id
    val contentOffset = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(1f) }
    var lastIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(selId) {
        val curIndex = state.profiles.indexOfFirst { it.id == selId }
        if (lastIndex >= 0 && curIndex >= 0 && curIndex != lastIndex) {
            val dir = if (curIndex > lastIndex) 1 else -1
            contentOffset.snapTo(with(density) { 48.dp.toPx() } * dir)
            contentAlpha.snapTo(0.2f)
            launch { contentAlpha.animateTo(1f, tween(300)) }
            contentOffset.animateTo(0f, tween(300))
        }
        lastIndex = curIndex
    }

    Column(Modifier.fillMaxSize().background(OloColors.Background)) {
        MemberSwitcher(
            title = if (tab == Tab.HOME) "OLO Cycle" else tab.label,
            profiles = state.profiles,
            selectedId = state.selected?.id,
            onSelect = vm::select,
            onAdd = { showAdd = true },
            onEditCurrent = { state.selected?.let { editProfile = it } },
            onSettings = { showSettings = true },
        )
        Box(
            Modifier.weight(1f).pointerInput(state.profiles, state.selected?.id) {
                val threshold = 56.dp.toPx()
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total <= -threshold) switchMember(+1)      // ← 다음 구성원
                        else if (total >= threshold) switchMember(-1)  // → 이전 구성원
                    },
                ) { change, dragAmount -> total += dragAmount; change.consume() }
            },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = contentOffset.value; alpha = contentAlpha.value }) {
                when (tab) {
                    Tab.HOME -> HomeDashboard(state, profileColor,
                        onLogToday = { recordDate = state.today },
                        onOpenCalendar = { tab = Tab.CALENDAR })
                    Tab.CALENDAR -> CalendarTab(state, profileColor, visibleMonth,
                        onSetMonth = { visibleMonth = it },
                        onDayClick = { recordDate = it })
                    Tab.RECORD -> RecordTab(state) { recordDate = it }
                    Tab.STATS -> StatsTab(state, profileColor)
                }
            }
        }
        BottomBar(tab, onSelect = { tab = it }, onFab = { recordDate = state.today })
    }

    if (showAdd) {
        ProfileEditorDialog(
            original = null,
            onDismiss = { showAdd = false },
            onSave = { name, color, cycle, period, birth, photo ->
                vm.addProfile(name, color, cycle, period, birth, photo); showAdd = false
            },
            onDelete = null,
        )
    }
    editProfile?.let { profile ->
        ProfileEditorDialog(
            original = profile,
            onDismiss = { editProfile = null },
            onSave = { name, color, cycle, period, birth, photo ->
                vm.updateProfile(profile, name, color, cycle, period, birth, photo)
                editProfile = null
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

// ---------------------------------------------------------------------------- top member switcher + bottom bar

/**
 * 최상단 고정 구성원 스위처(모든 화면 공통 앵커). 상단에 화면 제목 + 편집(현재 구성원)·설정 ⚙,
 * 아래에 구성원 아바타 줄(활성은 크게·색 링, 나머지는 작게). 눌러서 전환, ＋로 추가.
 */
@Composable
private fun MemberSwitcher(
    title: String, profiles: List<Profile>, selectedId: Long?,
    onSelect: (Long) -> Unit, onAdd: () -> Unit, onEditCurrent: () -> Unit, onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(OloColors.Surface).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp, 12.dp, 6.dp, 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), color = OloColors.Ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            if (selectedId != null) {
                IconButton(onEditCurrent) { Icon(Icons.Default.Edit, "현재 구성원 편집", tint = OloColors.Muted) }
            }
            IconButton(onSettings) { Icon(Icons.Default.Settings, "설정", tint = OloColors.Muted) }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp, 2.dp, 16.dp, 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            profiles.forEach { p ->
                val on = p.id == selectedId
                Column(
                    Modifier.clickable { onSelect(p.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    ProfileAvatar(
                        profile = p, size = if (on) 48.dp else 38.dp,
                        modifier = if (on) Modifier else Modifier.graphicsLayer { alpha = 0.6f },
                        borderColor = if (on) Color(p.color) else null, borderWidth = 3.dp,
                    )
                    Text(p.name, fontSize = 11.sp, maxLines = 1,
                        color = if (on) OloColors.Ink else OloColors.Muted,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
            Column(
                Modifier.clickable(onClick = onAdd), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).border(2.dp, OloColors.Outline, CircleShape),
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "구성원 추가", tint = OloColors.Outline) }
                Text("추가", fontSize = 11.sp, color = OloColors.Muted)
            }
        }
        HorizontalDivider(color = OloColors.Line)
    }
}

/** 하단 4탭 + 가운데 ＋기록 FAB. 탭은 좌2·우2로 나뉘고 FAB가 중앙에 떠 있다. */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit, onFab: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = OloColors.Line)
            Row(Modifier.fillMaxWidth().background(OloColors.Surface).navigationBarsPadding().height(64.dp),
                verticalAlignment = Alignment.CenterVertically) {
                NavItem(Modifier.weight(1f), Tab.HOME, current, onSelect)
                NavItem(Modifier.weight(1f), Tab.CALENDAR, current, onSelect)
                Spacer(Modifier.width(72.dp)) // FAB 자리
                NavItem(Modifier.weight(1f), Tab.RECORD, current, onSelect)
                NavItem(Modifier.weight(1f), Tab.STATS, current, onSelect)
            }
        }
        // 바 위쪽 경계에 반쯤 걸치도록 띄운 도킹 FAB(그림자로 떠 있는 느낌).
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-27).dp).size(56.dp)
                .shadow(8.dp, CircleShape).clip(CircleShape)
                .background(OloColors.Primary).clickable(onClick = onFab),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Add, "기록 추가", tint = Color.White, modifier = Modifier.size(26.dp)) }
    }
}

@Composable
private fun NavItem(modifier: Modifier, tab: Tab, current: Tab, onSelect: (Tab) -> Unit) {
    val on = tab == current
    Column(
        modifier.clickable { onSelect(tab) }, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, tab.label, tint = if (on) OloColors.Primary else OloColors.Muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(tab.label, fontSize = 11.sp, color = if (on) OloColors.Primary else OloColors.Muted,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Decodes a profile photo path into an [ImageBitmap], cached per path. Null path/decode → colour+initial. */
@Composable
private fun rememberProfileBitmap(path: String?): ImageBitmap? =
    remember(path) { path?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() } }

/**
 * 프로필 아바타: 사진이 있으면 사진, 없으면 프로필 색 + 이름 이니셜. 사진·색은 프로필 고유 정체성이라
 * 헤더·목록·편집창에서 같은 규칙으로 보여 준다.
 */
@Composable
private fun ProfileAvatar(
    profile: Profile,
    size: Dp,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    borderWidth: Dp = 2.dp,
) {
    val bmp = rememberProfileBitmap(profile.photoPath)
    Box(
        modifier.size(size).clip(CircleShape).background(Color(profile.color))
            .then(if (borderColor != null) Modifier.border(borderWidth, borderColor, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp, contentDescription = profile.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(profile.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
        }
    }
}

@Composable
private fun AppLockGate(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(OloColors.Background).padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(OloColors.AccentContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Lock, null, tint = OloColors.Primary, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("OLO Cycle 잠금", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OloColors.Ink)
        Spacer(Modifier.height(4.dp))
        Text("생체인증 또는 화면 잠금으로 확인하세요.", color = OloColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onUnlock, colors = ButtonDefaults.buttonColors(containerColor = OloColors.Primary), shape = RoundedCornerShape(22.dp)) {
            Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("잠금 해제", fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------------------- home (dashboard)

/** 홈 = 대시보드: 상태 히어로 · 빠른 기록 · 다가오는 일정 · 이번 주 스트립. */
@Composable
private fun HomeDashboard(
    state: HomeState, profileColor: Color, onLogToday: () -> Unit, onOpenCalendar: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Box(Modifier.padding(top = 14.dp).height(4.dp).width(46.dp).clip(RoundedCornerShape(3.dp)).background(profileColor))
        Spacer(Modifier.height(12.dp))
        HeroCard(state, profileColor)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onLogToday, enabled = state.selected != null,
            colors = ButtonDefaults.buttonColors(containerColor = OloColors.Primary),
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Icon(Icons.Default.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("오늘 기록", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        Spacer(Modifier.height(12.dp))
        UpcomingCard(state)
        Spacer(Modifier.height(12.dp))
        WeekStrip(state, profileColor, onOpenCalendar)
        Text("예측은 참고용 추정치이며 피임·진단의 근거가 아닙니다.",
            Modifier.fillMaxWidth().padding(vertical = 16.dp), color = OloColors.Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

/** 상태 히어로 카드: 구성원 색 그라데이션 + 단계·D-day·다음 예정 + 미니 링. */
@Composable
private fun HeroCard(state: HomeState, profileColor: Color) {
    val d = state.daysUntilNextPeriod
    val cycle = state.params?.cycleLength ?: 28
    Box(
        Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(lerp(profileColor, Color.White, 0.12f), lerp(profileColor, Color.Black, 0.16f))))
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(0.68f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("● ${phaseName(state.currentPhase())}" + (state.cycleDayIndex?.let { " · 주기 ${it}일째" } ?: ""),
                color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(when { d == null -> "기록 전"; d >= 0 -> "D-$d"; else -> "D+${-d}" },
                color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 48.sp)
            Text(state.prediction?.let { "다음 생리 ${it.nextPeriodStart.monthValue}/${it.nextPeriodStart.dayOfMonth} 예정" }
                ?: "생리 시작일을 기록해 보세요", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1)
        }
        Box(Modifier.align(Alignment.CenterEnd).size(80.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 7.dp.toPx()
                val r = (size.minDimension - sw) / 2f
                val tl = Offset(center.x - r, center.y - r); val sz = Size(r * 2, r * 2)
                drawCircle(Color.White.copy(alpha = 0.28f), r, style = Stroke(sw))
                state.cycleDayIndex?.let { idx ->
                    drawArc(Color.White, -90f, (idx.toFloat() / cycle * 360f), false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                    val ang = Math.toRadians((-90f + idx.toFloat() / cycle * 360f).toDouble())
                    drawCircle(Color.White, 5.dp.toPx(), Offset(center.x + r * cos(ang).toFloat(), center.y + r * sin(ang).toFloat()))
                }
            }
        }
    }
}

/** 다가오는 일정: 다음 생리·배란·가임기와 각 D-day. */
@Composable
private fun UpcomingCard(state: HomeState) {
    val pred = state.prediction ?: return
    val today = state.today
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OloColors.Surface)
            .border(1.dp, OloColors.Line, RoundedCornerShape(14.dp)).padding(15.dp, 13.dp),
    ) {
        Text("다가오는 일정", color = OloColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        UpcomingRow(OloColors.Period, "다음 생리", pred.nextPeriodStart, today)
        UpcomingRow(OloColors.Ovulation, "배란", pred.ovulation, today)
        UpcomingRow(OloColors.Fertile, "가임기", pred.fertileStart, today,
            trailing = "${pred.fertileStart.monthValue}/${pred.fertileStart.dayOfMonth}~${pred.fertileEnd.dayOfMonth}")
    }
}

@Composable
private fun UpcomingRow(color: Color, label: String, date: LocalDate, today: LocalDate, trailing: String? = null) {
    val d = today.until(date).days
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, color = OloColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(trailing ?: "${date.monthValue}/${date.dayOfMonth}", color = OloColors.Muted, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(if (d >= 0) "D-$d" else "D+${-d}", color = OloColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** 이번 주 스트립: 일~토, 단계색으로 칠하고 오늘 강조. 누르면 달력 탭으로. */
@Composable
private fun WeekStrip(state: HomeState, profileColor: Color, onOpenCalendar: () -> Unit) {
    val today = state.today
    val sunday = today.minusDays((today.dayOfWeek.value % 7).toLong())
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OloColors.Surface)
            .border(1.dp, OloColors.Line, RoundedCornerShape(14.dp)).clickable(onClick = onOpenCalendar).padding(13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("이번 주", color = OloColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("달력 보기 ›", color = profileColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val labels = listOf("일", "월", "화", "수", "목", "금", "토")
            (0..6).forEach { i ->
                val day = sunday.plusDays(i.toLong())
                val (bg, fg) = phaseColors(state.phaseOf(day))
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg)
                        .then(if (day == today) Modifier.border(2.dp, profileColor, RoundedCornerShape(10.dp)) else Modifier)
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(labels[i], fontSize = 9.sp, color = if (bg == Color.White) OloColors.Muted else fg)
                    Text("${day.dayOfMonth}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- calendar tab

/** 달력 = 독립 전체화면 탭: 큰 월간 달력 + 범례. 제목 탭 → 연·월 피커, 그리드 좌우 스와이프 → 월 이동. */
@Composable
private fun CalendarTab(
    state: HomeState, profileColor: Color, visibleMonth: YearMonth,
    onSetMonth: (YearMonth) -> Unit, onDayClick: (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    // 월이 바뀌면 이동 방향에서 달력이 슬라이드해 들어온다.
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.padding(start = 16.dp, top = 12.dp).height(4.dp).width(46.dp).clip(RoundedCornerShape(3.dp)).background(profileColor))
        MonthHeader(
            visibleMonth, profileColor,
            onPrev = { onSetMonth(visibleMonth.minusMonths(1)) },
            onNext = { onSetMonth(visibleMonth.plusMonths(1)) },
            onTitleClick = { showPicker = true },
            onToday = { onSetMonth(YearMonth.now()) },
        )
        Box(
            Modifier.fillMaxWidth().pointerInput(visibleMonth) {
                val threshold = 56.dp.toPx()
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total <= -threshold) onSetMonth(visibleMonth.plusMonths(1))       // ← 다음 달
                        else if (total >= threshold) onSetMonth(visibleMonth.minusMonths(1))  // → 이전 달
                    },
                ) { change, dragAmount -> total += dragAmount; change.consume() }
            },
        ) {
            Box(Modifier.graphicsLayer { translationX = gridOffset.value }) {
                MonthCalendar(visibleMonth, state.today, profileColor, state::phaseOf, state::recordOf,
                    enabled = state.selected != null, onDayClick = onDayClick)
            }
        }
        PhaseLegend()
        Text("표를 좌우로 밀어 달을 넘길 수 있어요. 예측은 참고용 추정치이며 피임·진단의 근거가 아닙니다.",
            Modifier.fillMaxWidth().padding(16.dp), color = OloColors.Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
    if (showPicker) {
        MonthYearPickerDialog(visibleMonth, profileColor, state.today,
            onPick = { onSetMonth(it); showPicker = false },
            onDismiss = { showPicker = false })
    }
}

/** 연·월 직접 선택: 연도 스테퍼 + 12개월 그리드. 선택 월·이번 달을 강조. */
@Composable
private fun MonthYearPickerDialog(
    month: YearMonth, accent: Color, today: LocalDate,
    onPick: (YearMonth) -> Unit, onDismiss: () -> Unit,
) {
    var year by remember { mutableStateOf(month.year) }
    val thisMonth = YearMonth.from(today)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ year-- }) { Icon(Icons.Default.ChevronLeft, "이전 해", tint = OloColors.Muted) }
                Text("${year}년", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = OloColors.Ink)
                IconButton({ year++ }) { Icon(Icons.Default.ChevronRight, "다음 해", tint = OloColors.Muted) }
            }
        },
        text = {
            Column {
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
                TextButton(onClick = { onPick(thisMonth) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("이번 달로", color = accent, fontWeight = FontWeight.Bold)
                }
            }
        },
    )
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
private fun MonthHeader(
    month: YearMonth, profileColor: Color,
    onPrev: () -> Unit, onNext: () -> Unit, onTitleClick: () -> Unit, onToday: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp, 8.dp, 2.dp), verticalAlignment = Alignment.CenterVertically) {
        // 제목을 누르면 연·월 피커. 캐럿으로 눌러 고를 수 있음을 표시.
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onTitleClick).padding(6.dp, 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${month.year}년 ${month.monthValue}월", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = profileColor)
            Icon(Icons.Default.ArrowDropDown, "연·월 선택", tint = profileColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onToday, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            Text("오늘", color = OloColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
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
            EmptyHint("아래 ＋ 버튼이나 달력 날짜를 눌러 기록을 남겨 보세요.")
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
        Spacer(Modifier.height(16.dp))
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
private fun SettingsScreen(
    appLockEnabled: Boolean,
    onToggleAppLock: (Boolean) -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit,
) {
    // 설정은 앱 전역 전체화면(탭 아님). 상단 ← 로 돌아간다. 개인별 설정은 각 프로필(상단 연필)에.
    Column(Modifier.fillMaxSize().background(OloColors.Background)) {
        Row(Modifier.fillMaxWidth().background(OloColors.Surface).statusBarsPadding().padding(6.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Default.ChevronLeft, "뒤로", tint = OloColors.Ink) }
            Text("설정", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = OloColors.Ink)
        }
        HorizontalDivider(color = OloColors.Line)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("설정 · 개인정보", "앱 전체 공통 항목")
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
        // 앱 잠금(앱 전체 공통). 켜면 첫 구동·복귀 때마다 인증을 요구한다.
        Row(Modifier.fillMaxWidth().padding(20.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("앱 잠금", fontWeight = FontWeight.SemiBold, color = OloColors.Ink)
                Text("앱을 열 때·다른 앱에서 돌아올 때 생체인증/PIN을 요구합니다", color = OloColors.Muted, fontSize = 12.sp)
            }
            Switch(appLockEnabled, onToggleAppLock)
        }
        HorizontalDivider(color = OloColors.Line)
        Text("구성원 추가는 상단 ＋, 이름·사진·색상·주기 수정은 상단 연필(현재 구성원)에서 합니다. 구성원 이동은 상단 아바타를 누르거나 화면을 좌우로 미세요.",
            Modifier.padding(20.dp, 12.dp), color = OloColors.Muted, fontSize = 12.sp, lineHeight = 18.sp)
        HorizontalDivider(color = OloColors.Line)
        SettingRow("앱 정보 · 오픈소스 고지", onClick = onAbout)
        }
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
                    "· 디자인: OLO 디자인 시스템 (Apache/MIT 오픈소스 기반)",
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
    onSave: (name: String, color: Int, cycle: Int, period: Int, birthControl: Boolean, photoPath: String?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var colorIndex by remember {
        mutableStateOf(original?.let { p -> OloColors.ProfilePalette.indexOfFirst { it.toArgb() == p.color }.coerceAtLeast(0) } ?: 0)
    }
    var cycle by remember { mutableStateOf(original?.defaultCycleLength ?: 28) }
    var period by remember { mutableStateOf(original?.defaultPeriodLength ?: 5) }
    var birthControl by remember { mutableStateOf(original?.onBirthControl ?: false) }
    var photoPath by remember { mutableStateOf(original?.photoPath) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // 시스템 사진 선택기(권한 불필요). 고른 이미지는 내부 저장소로 복사하고 경로만 기억한다.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val saved = ProfilePhotos.copyToInternal(ctx, uri)
            if (saved != null) {
                if (photoPath != null && photoPath != original?.photoPath) ProfilePhotos.delete(photoPath) // 저장 전 교체분 정리
                photoPath = saved
            } else {
                Toast.makeText(ctx, "사진을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(name, OloColors.ProfilePalette[colorIndex].toArgb(), cycle, period, birthControl, photoPath) }) {
                Text(if (original == null) "만들기" else "저장")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        title = { Text(if (original == null) "구성원 추가" else "프로필 편집") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // 사진: 있으면 미리보기, 없으면 색+이니셜. 선택/제거 버튼을 옆에.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val previewBmp = rememberProfileBitmap(photoPath)
                    Box(Modifier.size(64.dp).clip(CircleShape).background(OloColors.ProfilePalette[colorIndex]), contentAlignment = Alignment.Center) {
                        if (previewBmp != null) {
                            Image(previewBmp, "프로필 사진", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(name.take(1).ifBlank { "?" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        TextButton(onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text(if (photoPath == null) "사진 추가" else "사진 변경") }
                        if (photoPath != null) {
                            TextButton(onClick = {
                                if (photoPath != original?.photoPath) ProfilePhotos.delete(photoPath) // 저장 전 새로 만든 파일이면 정리
                                photoPath = null
                            }) { Text("사진 제거", color = OloColors.Muted) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
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

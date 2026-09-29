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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
    HOME("오늘", Icons.Default.Home),
    CALENDAR("달력", Icons.Default.CalendarMonth),
    ANALYSIS("분석", Icons.Default.BarChart),
}

@Composable
private fun App(vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val appLockEnabled by vm.appLockEnabled.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? FragmentActivity
    // 화면 상태는 잠금 게이트보다 위에 두어(그리고 rememberSaveable로) 잠금·프로세스 재생성 후에도 보존한다.
    // (게이트 아래에 두면 잠금 때 컴포지션에서 빠져 리셋 → 해제 후 항상 홈으로 떨어지는 버그가 있었다.)
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    var showAdd by remember { mutableStateOf(false) }
    var editProfile by remember { mutableStateOf<Profile?>(null) }
    var recordDate by remember { mutableStateOf<LocalDate?>(null) }
    var showAbout by remember { mutableStateOf(false) }

    // 앱 잠금(프로필 무관, 앱 전체 1개). 첫 구동엔 잠긴 상태로 시작하고, 홈/타 앱으로 나갔다 오면 다시 잠근다.
    var authed by rememberSaveable { mutableStateOf(false) }
    var authInProgress by remember { mutableStateOf(false) }
    // 파일 선택기(SAF)처럼 앱 내부 작업으로 잠깐 나갈 때는 재잠금을 억제한다(인증창 이탈과 같은 처리).
    var suppressRelock by remember { mutableStateOf(false) }
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
                    if (!authInProgress && !suppressRelock && activity?.isChangingConfigurations != true) authed = false
                Lifecycle.Event.ON_RESUME -> {
                    suppressRelock = false // 파일 선택 등에서 돌아오면 억제 해제(다음 실제 백그라운드부터 다시 잠금).
                    if (appLockEnabled && !authed && !authInProgress) requestUnlock()
                }
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

    // 설정은 앱 전역 → 탭이 아니라 우상단 ⚙ 로 진입하는 전체화면. 구성원 스위처를 띄우지 않는다.
    if (showSettings) {
        BackHandler { showSettings = false } // 뒤로 가기 = 설정 닫고 이전 화면으로(앱 종료 아님).
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
            onExport = { pass -> vm.exportEncrypted(pass) },
            onImport = { blob, pass, mode -> vm.importEncrypted(blob, pass, mode) },
            onExternalPick = { suppressRelock = true }, // SAF 열기 직전 재잠금 억제
        )
        if (showAbout) AboutDialog { showAbout = false }
        return
    }

    // 앱 전체 뒤로 가기: 홈이 아니면 홈으로, 홈이면 기본 동작(백그라운드). 다이얼로그는 각자 back으로 닫힌다.
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

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
            tabLabel = tab.label,
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
                        onOpenCalendar = { tab = Tab.CALENDAR })
                    Tab.CALENDAR -> CalendarTab(state, profileColor, visibleMonth,
                        onSetMonth = { visibleMonth = it },
                        onDayClick = { recordDate = it })
                    Tab.ANALYSIS -> AnalysisTab(state, profileColor) { recordDate = it }
                }
            }
        }
        BottomBar(tab, profileColor, onSelect = { tab = it }, onFab = { recordDate = state.today })
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
 * 최상단 고정 구성원 스위처(모든 화면 공통 앵커) — 한 줄 사각형 바.
 * 제목 오른쪽에 사각 타일: 활성은 색 배경+2글자 이름으로 확장, 비활성은 사각 아바타 아이콘만. ＋추가, ✎편집, ⚙설정.
 */
@Composable
private fun MemberSwitcher(
    tabLabel: String, profiles: List<Profile>, selectedId: Long?,
    onSelect: (Long) -> Unit, onAdd: () -> Unit, onEditCurrent: () -> Unit, onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(OloColors.Surface).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 8.dp, 4.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 브랜드 워드마크(순환 고리 + OLO Cycle) 위, 화면 이름 아래 2단. 브랜드가 주인공, 화면명은 보조.
            OloRingMark(size = 22.dp, color = OloColors.Primary)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = OloColors.Primary, fontWeight = FontWeight.ExtraBold)) { append("OLO") }
                        withStyle(SpanStyle(color = OloColors.Ink, fontWeight = FontWeight.ExtraBold)) { append(" Cycle") }
                    },
                    fontSize = 17.sp, lineHeight = 19.sp, maxLines = 1,
                )
                Text(tabLabel, color = OloColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                profiles.forEach { p ->
                    val on = p.id == selectedId
                    if (on) {
                        // 활성: 이름 한 번만. 사진 있으면 사진+이름, 없으면 이름만(색 배경으로 정체성 표시). 다시 누르면 편집.
                        val hasPhoto = p.photoPath != null
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(Color(p.color).copy(alpha = 0.18f))
                                .clickable { onEditCurrent() }
                                .padding(start = if (hasPhoto) 4.dp else 12.dp, top = 5.dp, end = 11.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (hasPhoto) { ProfileAvatar(p, size = 28.dp, square = true); Spacer(Modifier.width(7.dp)) }
                            Text(p.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = OloColors.Ink, maxLines = 1)
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.Default.Edit, "이 구성원 편집", tint = OloColors.Muted, modifier = Modifier.size(13.dp))
                        }
                    } else {
                        // 비활성: 사각 아바타(사진 또는 색+2글자)만. 옆에 이름 없음 → 중복 없음.
                        ProfileAvatar(p, size = 32.dp, square = true,
                            modifier = Modifier.graphicsLayer { alpha = 0.82f }.clickable { onSelect(p.id) })
                    }
                }
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).border(1.6.dp, OloColors.Outline, RoundedCornerShape(9.dp))
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Add, "구성원 추가", tint = OloColors.Outline, modifier = Modifier.size(18.dp)) }
            }
            IconButton(onSettings, Modifier.size(38.dp)) { Icon(Icons.Default.Settings, "설정", tint = OloColors.Muted, modifier = Modifier.size(19.dp)) }
        }
        HorizontalDivider(color = OloColors.Line)
    }
}

/** 앱 상징 '순환 고리' 마크 — 살짝 트인 원. 브랜드색(클레이)으로 헤더 워드마크 앞에 둔다. */
@Composable
private fun OloRingMark(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val sw = size.toPx() * 0.18f
        val r = (this.size.minDimension - sw) / 2f
        val tl = Offset(center.x - r, center.y - r); val sz = Size(r * 2, r * 2)
        drawArc(color, -48f, 300f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

/** 하단 3탭 + 오른쪽 ＋기록. 활성 탭·FAB는 활성 구성원 색(B안 강조). */
@Composable
private fun BottomBar(current: Tab, accent: Color, onSelect: (Tab) -> Unit, onFab: () -> Unit) {
    // 3개 뷰 탭(오늘·달력·분석) + 오른쪽 끝에 기록 추가 ＋ 하나. 바 안에 두어 콘텐츠와 겹치지 않는다.
    Column {
        HorizontalDivider(color = OloColors.Line)
        Row(Modifier.fillMaxWidth().background(OloColors.Surface).navigationBarsPadding().height(64.dp),
            verticalAlignment = Alignment.CenterVertically) {
            NavItem(Modifier.weight(1f), Tab.HOME, current, accent, onSelect)
            NavItem(Modifier.weight(1f), Tab.CALENDAR, current, accent, onSelect)
            NavItem(Modifier.weight(1f), Tab.ANALYSIS, current, accent, onSelect)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(
                        Modifier.size(44.dp).shadow(4.dp, CircleShape).clip(CircleShape)
                            .background(accent).clickable(onClick = onFab),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Add, "기록 추가", tint = Color.White, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.height(2.dp))
                    Text("기록", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NavItem(modifier: Modifier, tab: Tab, current: Tab, accent: Color, onSelect: (Tab) -> Unit) {
    val on = tab == current
    Column(
        modifier.clickable { onSelect(tab) }, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, tab.label, tint = if (on) accent else OloColors.Muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(tab.label, fontSize = 11.sp, color = if (on) accent else OloColors.Muted,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

/**
 * 사각 아바타에 넣을 글자: 최소 2자. 한국 성명 관습(성1+이름2, 남궁·황보 등 복성)에서 이름은 보통 끝 2자이므로
 * 3자 이상이면 끝 2자(이름), 2자 이하면 그대로. (예: 김하늘→"하늘", 남궁민수→"민수", 김철→"김철", 정→"정")
 */
private fun avatarInitials(name: String): String {
    val n = name.trim()
    return when {
        n.isEmpty() -> "?"
        n.length <= 2 -> n
        else -> n.takeLast(2)
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
    square: Boolean = false,
) {
    val bmp = rememberProfileBitmap(profile.photoPath)
    val shape = if (square) RoundedCornerShape(size * 0.24f) else CircleShape
    Box(
        modifier.size(size).clip(shape).background(Color(profile.color))
            .then(if (borderColor != null) Modifier.border(borderWidth, borderColor, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp, contentDescription = profile.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            // 사각 타일은 2글자(성명 관습: 성1+이름2 → 이름 끝 2자), 원형은 1글자.
            val label = if (square) avatarInitials(profile.name) else profile.name.take(1)
            Text(label, color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = (size.value * (if (square && label.length >= 2) 0.30f else 0.38f)).sp, maxLines = 1)
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

/** 홈 = 오늘: 상태 히어로 · 다가오는 일정 · 이번 주. 기록 추가는 하단 ＋ 로 통일(중복 버튼 없음). */
@Composable
private fun HomeDashboard(
    state: HomeState, profileColor: Color, onOpenCalendar: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(14.dp))
        HeroCard(state, profileColor)
        Spacer(Modifier.height(12.dp))
        UpcomingCard(state, profileColor)
        Spacer(Modifier.height(12.dp))
        WeekStrip(state, profileColor, onOpenCalendar)
        state.cycleDayIndex?.let { idx ->
            Spacer(Modifier.height(12.dp))
            CycleProgressCard(idx, state.params?.cycleLength ?: 28, phaseName(state.currentPhase()), profileColor)
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 이번 주기 진행 — 홈 하단 여백 활용(면책 문구 자리). 진행 바는 활성 구성원 색. */
@Composable
private fun CycleProgressCard(dayIndex: Int, cycleLength: Int, phase: String, accent: Color) {
    AccentCard(accent) {
        Text("이번 주기 진행", color = OloColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(9.dp))
        val frac = (dayIndex.toFloat() / cycleLength).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(7.dp)).background(OloColors.SurfaceSoft)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(7.dp)).background(accent))
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("$phase · ${dayIndex}일째", color = OloColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${cycleLength}일 주기", color = OloColors.Muted, fontSize = 12.sp)
        }
    }
}

/** 왼쪽에 구성원 색 세로 라인을 둔 카드 — "지금 누구의 내용인지" 한눈에 식별. */
@Composable
private fun AccentCard(accent: Color, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(14.dp))
            .background(OloColors.Surface).border(1.dp, OloColors.Line, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
        Column(Modifier.weight(1f).padding(15.dp, 13.dp), content = content)
    }
}

/** 상태 히어로 카드: 구성원 색 그라데이션 + 단계·D-day·다음 예정 + 미니 링. */
@Composable
private fun HeroCard(state: HomeState, profileColor: Color) {
    val d = state.daysUntilNextPeriod
    val cycle = state.params?.cycleLength ?: 28
    Box(
        // 고정 높이(clip)를 쓰면 큰 글꼴 기기에서 마지막 줄이 잘린다 → 최소 높이로 두어 내용에 맞게 늘어나게.
        Modifier.fillMaxWidth().heightIn(min = 140.dp).clip(RoundedCornerShape(20.dp))
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

/** 다가오는 일정: 다음 생리·배란·가임기와 각 D-day. 좌측 구성원 색 라인. */
@Composable
private fun UpcomingCard(state: HomeState, profileColor: Color) {
    val pred = state.prediction ?: return
    val today = state.today
    AccentCard(profileColor) {
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

/** 이번 주 스트립: 일~토, 단계색으로 칠하고 오늘 강조. 누르면 달력 탭으로. 좌측 구성원 색 라인. */
@Composable
private fun WeekStrip(state: HomeState, profileColor: Color, onOpenCalendar: () -> Unit) {
    val today = state.today
    val sunday = today.minusDays((today.dayOfWeek.value % 7).toLong())
    AccentCard(profileColor, onClick = onOpenCalendar) {
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
                val isToday = day == today
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg).padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(labels[i], fontSize = 9.sp, color = if (bg == Color.White) OloColors.Muted else fg)
                    Spacer(Modifier.height(2.dp))
                    // 오늘 = 숫자 뒤 구성원색 원(달력과 동일 규칙).
                    if (isToday) {
                        Box(Modifier.size(21.dp).clip(CircleShape).background(profileColor), contentAlignment = Alignment.Center) {
                            Text("${day.dayOfMonth}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    } else {
                        Text("${day.dayOfMonth}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- calendar tab

private enum class CalMode { MONTH, YEAR }

/** 달력 탭: 월/연 전환. 월=큰 달력(칸 정보·스와이프·연월 피커), 연=12개월 패턴 한눈 보기. */
@Composable
private fun CalendarTab(
    state: HomeState, profileColor: Color, visibleMonth: YearMonth,
    onSetMonth: (YearMonth) -> Unit, onDayClick: (LocalDate) -> Unit,
) {
    var mode by remember { mutableStateOf(CalMode.MONTH) }
    var showPicker by remember { mutableStateOf(false) }
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
    Column(Modifier.fillMaxSize()) {
        // 상단 줄: 색 악센트 + 월/연 세그먼트 토글.
        Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp, 16.dp, 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(4.dp).width(46.dp).clip(RoundedCornerShape(3.dp)).background(profileColor))
            Spacer(Modifier.weight(1f))
            SegToggle(listOf("월", "연"), if (mode == CalMode.MONTH) 0 else 1, profileColor) {
                mode = if (it == 0) CalMode.MONTH else CalMode.YEAR
            }
        }
        if (mode == CalMode.MONTH) {
            MonthHeader(
                visibleMonth, profileColor,
                onPrev = { onSetMonth(visibleMonth.minusMonths(1)) },
                onNext = { onSetMonth(visibleMonth.plusMonths(1)) },
                onTitleClick = { showPicker = true },
                onToday = { onSetMonth(YearMonth.now()) },
            )
            Box(
                Modifier.fillMaxWidth().weight(1f).pointerInput(visibleMonth) {
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
                Box(Modifier.fillMaxSize().graphicsLayer { translationX = gridOffset.value }) {
                    MonthCalendar(visibleMonth, state.today, profileColor, state::phaseOf, state::recordOf,
                        state::calendarCellLabel, enabled = state.selected != null, onDayClick = onDayClick, fillHeight = true)
                }
            }
            Box(Modifier.padding(bottom = 8.dp)) { PhaseLegend() }
        } else {
            YearView(
                year = visibleMonth.year, today = state.today, profileColor = profileColor,
                phaseOf = state::phaseOf,
                onPrevYear = { onSetMonth(visibleMonth.minusYears(1)) },
                onNextYear = { onSetMonth(visibleMonth.plusYears(1)) },
                onToday = { onSetMonth(YearMonth.now()) },
                onPickMonth = { m -> onSetMonth(YearMonth.of(visibleMonth.year, m)); mode = CalMode.MONTH },
            )
        }
    }
    if (showPicker) {
        MonthYearPickerDialog(visibleMonth, profileColor, state.today,
            onPick = { onSetMonth(it); showPicker = false },
            onDismiss = { showPicker = false })
    }
}

/** 작은 세그먼트 토글(월/연 등). */
@Composable
private fun SegToggle(items: List<String>, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
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
private fun YearView(
    year: Int, today: LocalDate, profileColor: Color, phaseOf: (LocalDate) -> Phase,
    onPrevYear: () -> Unit, onNextYear: () -> Unit, onToday: () -> Unit, onPickMonth: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp, 8.dp, 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${year}년", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = profileColor)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToday, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Text("올해", color = OloColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onPrevYear) { Icon(Icons.Default.ChevronLeft, "이전 해", tint = OloColors.Muted) }
            IconButton(onNextYear) { Icon(Icons.Default.ChevronRight, "다음 해", tint = OloColors.Muted) }
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)) {
            (0..3).forEach { row ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    (1..3).forEach { col ->
                        val m = row * 3 + col
                        Box(Modifier.weight(1f).fillMaxHeight().padding(5.dp)) {
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
private fun YearLegend() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendItem("생리", OloColors.Period)
        LegendItem("예측", OloColors.PeriodLight)
        LegendItem("가임기", OloColors.FertileLight)
        LegendItem("배란", OloColors.Ovulation)
    }
}

/** 연 달력의 한 달 미니 그리드. */
@Composable
private fun MiniMonth(month: YearMonth, today: LocalDate, profileColor: Color, phaseOf: (LocalDate) -> Phase, onClick: () -> Unit) {
    val leading = month.atDay(1).dayOfWeek.value % 7
    val cells = (0 until leading).map<Int, LocalDate?> { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(OloColors.Surface)
            .border(1.dp, OloColors.Line, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(6.dp, 5.dp),
    ) {
        Text("${month.monthValue}월", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = profileColor)
        Spacer(Modifier.height(3.dp))
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                    week.forEach { day ->
                        // 오늘은 구성원색 solid 칸으로(연 뷰에선 숫자가 없으므로 꽉 채워 또렷하게).
                        val c = when {
                            day == null -> Color.Transparent
                            day == today -> profileColor
                            else -> miniColor(phaseOf(day))
                        }
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c))
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 연 달력용 단계별 채움색(작은 칸이라 진한 원색 대신 톤을 통일). */
private fun miniColor(phase: Phase): Color = when (phase) {
    Phase.PERIOD -> OloColors.Period
    Phase.PREDICTED_PERIOD -> OloColors.PeriodLight
    Phase.FERTILE -> OloColors.FertileLight
    Phase.OVULATION -> OloColors.Ovulation
    else -> Color(0x11000000)
}

/** 연·월 직접 선택: 연도 스테퍼 + 12개월 그리드. 선택 월·이번 달을 강조. */
@Composable
private fun MonthYearPickerDialog(
    month: YearMonth, accent: Color, today: LocalDate,
    onPick: (YearMonth) -> Unit, onDismiss: () -> Unit,
) {
    var year by remember { mutableStateOf(month.year) }
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
        TextButton(onClick = { onPick(thisMonth) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("이번 달로", color = accent, fontWeight = FontWeight.Bold)
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
    Phase.PMS -> "황체기" // 생리 직전 며칠(색상은 유지하되 별도 문구 없이 황체기로 표기)
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
private fun DayCell(
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
private fun TodayAwareDayNumber(dayOfMonth: Int, isToday: Boolean, accent: Color, fg: Color) {
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

/** 범례 — 각 칸을 실제 달력 칸의 축소판으로 그려 달력 색과 정확히 일치시킨다. */
@Composable
private fun PhaseLegend() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendItem("생리", OloColors.Period)                                   // 꽉 찬 로즈
        LegendItem("예측", OloColors.PeriodLight, dashBorder = OloColors.Period) // 연분홍 + 점선
        LegendItem("가임기", OloColors.FertileLight)                           // 연청록
        LegendItem("배란", OloColors.OvulationLight, ring = OloColors.Ovulation) // 연회색 + 고리
    }
}

/** 달력 칸과 같은 규칙(채움색·점선 테두리·배란 고리)으로 그린 범례 스와치. */
@Composable
private fun LegendItem(label: String, fill: Color, dashBorder: Color? = null, ring: Color? = null) {
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

// ---------------------------------------------------------------------------- analysis tab (기록+통계 통합)

/** 분석 = 요약 KPI · 증상 인사이트 · 주기 추이 · 다음 예정 · 주기 히스토리 · 기록한 날. */
@Composable
private fun AnalysisTab(state: HomeState, profileColor: Color, onOpenDay: (LocalDate) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SectionTitle("분석", state.selected?.name?.let { "$it · 주기·증상 리포트" } ?: "")

        // 요약 KPI
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
        if (!state.isPersonalized) {
            Text("생리 시작을 3회 이상 기록하면 개인 평균으로 예측이 정확해집니다.",
                Modifier.padding(4.dp, 8.dp), color = OloColors.Muted, fontSize = 12.sp)
        }

        // 증상 인사이트 ⑤ — 자주/반복/예측
        val symptoms = state.symptomStats()
        if (symptoms.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
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

        // 주기 추이 막대
        if (state.recentCycleLengths.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            AccentCard(profileColor) {
                Text("최근 주기 길이(일)", color = OloColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val maxLen = state.recentCycleLengths.max().coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(100.dp), verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.recentCycleLengths.forEach { len ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$len", fontSize = 11.sp, color = OloColors.Muted)
                            Box(Modifier.fillMaxWidth().height((66 * len / maxLen).dp.coerceAtLeast(6.dp))
                                .clip(RoundedCornerShape(6.dp)).background(profileColor))
                        }
                    }
                }
            }
        }

        // 다음 예정
        state.prediction?.let {
            Spacer(Modifier.height(14.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = profileColor.copy(alpha = 0.10f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("다음 생리 예정", color = OloColors.Muted, fontSize = 12.sp)
                    Text("${it.nextPeriodStart.monthValue}월 ${it.nextPeriodStart.dayOfMonth}일" +
                        (state.daysUntilNextPeriod?.let { d -> " (D-$d)" } ?: ""),
                        color = OloColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("배란 예정 ${it.ovulation.monthValue}/${it.ovulation.dayOfMonth} · 가임기 ${it.fertileStart.monthValue}/${it.fertileStart.dayOfMonth}~${it.fertileEnd.dayOfMonth}",
                        color = OloColors.Muted, fontSize = 12.sp)
                }
            }
        }

        // 주기 히스토리 표
        val history = state.periodHistory()
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
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

        // 기록한 날 목록
        val days = state.loggedDays()
        Spacer(Modifier.height(14.dp))
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
        Spacer(Modifier.height(20.dp))
    }
}

/** 작은 칩 묶음(반복·예측 등). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(labels: List<String>, accent: Color) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { t ->
            Text(t, Modifier.clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.12f)).padding(9.dp, 4.dp),
                color = OloColors.Ink, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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

@Composable
private fun Kpi(modifier: Modifier, value: String, label: String, accent: Color) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(OloColors.SurfaceSoft).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = accent, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
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
    onExport: suspend (CharArray) -> ByteArray,
    onImport: suspend (ByteArray, CharArray, HomeViewModel.ImportMode) -> Int,
    onExternalPick: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var passExport by remember { mutableStateOf(false) }   // 내보내기 암호 입력창
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) } // 파일 고른 뒤 복원 암호 입력
    var confirmRestore by remember { mutableStateOf<Pair<ByteArray, CharArray>?>(null) } // 덮어쓰기 확인
    var busy by remember { mutableStateOf(false) }

    // 저장 위치 선택기(SAF) — 저장소 권한 불필요. 암호 상태를 클로저로 잡아 고른 위치에 암호문을 쓴다.
    var exportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pass = exportPassphrase
        exportPassphrase = null
        if (uri == null || pass == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = runCatching {
                val bytes = onExport(pass)
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("스트림 열기 실패")
            }.isSuccess
            busy = false
            Toast.makeText(ctx, if (ok) "암호화 백업을 저장했습니다." else "백업 저장에 실패했습니다.", Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImportUri = uri // 파일을 골랐으면 이어서 암호를 묻는다.
    }

    // 설정은 앱 전역 전체화면(탭 아님). 상단 ← 로 돌아간다. 개인별 설정은 각 프로필(상단 연필)에.
    Column(Modifier.fillMaxSize().background(OloColors.Background)) {
        Row(Modifier.fillMaxWidth().background(OloColors.Surface).statusBarsPadding().padding(6.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Default.ChevronLeft, "뒤로", tint = OloColors.Ink) }
            Text("설정", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = OloColors.Ink)
        }
        HorizontalDivider(color = OloColors.Line)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // 보안
            SettingsGroup("보안")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, OloColors.Line, RoundedCornerShape(12.dp))
                .background(OloColors.Surface).padding(15.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("앱 잠금", fontWeight = FontWeight.Bold, color = OloColors.Ink, fontSize = 14.sp)
                    Text("열 때·다른 앱에서 돌아올 때 생체인증/PIN 요구", color = OloColors.Muted, fontSize = 12.sp)
                }
                Switch(appLockEnabled, onToggleAppLock)
            }

            // 개인정보
            SettingsGroup("개인정보")
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = OloColors.AccentContainer)) {
                Column(Modifier.padding(15.dp)) {
                    Text("내 데이터는 이 기기에만 있습니다", color = OloColors.OnAccentContainer, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    listOf("인터넷 권한 없음 · 서버 전송 없음", "추적·광고 SDK 없음", "계정 없이 사용").forEach {
                        Text("· $it", color = OloColors.OnAccentContainer, fontSize = 12.5.sp, lineHeight = 19.sp)
                    }
                }
            }

            // 백업 — 암호화 파일로 내보내고, 그 파일에서 복원. 인터넷 없이 기기 파일로만.
            SettingsGroup("백업")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, OloColors.Line, RoundedCornerShape(12.dp))
                .background(OloColors.Surface)) {
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { passExport = true }.padding(15.dp, 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("암호화 백업 내보내기", color = OloColors.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("전체 데이터를 암호로 잠근 파일로 저장", color = OloColors.Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = OloColors.Muted)
                }
                HorizontalDivider(color = OloColors.Line)
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { onExternalPick(); importLauncher.launch(arrayOf("*/*")) }.padding(15.dp, 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("백업에서 복원", color = OloColors.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("현재 데이터를 백업 내용으로 대체", color = OloColors.Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = OloColors.Muted)
                }
            }
            Text("비밀번호는 복원할 때 반드시 필요합니다. 잊으면 백업을 열 수 없습니다(기기에만 저장, 복구 불가).",
                Modifier.padding(4.dp, 8.dp), color = OloColors.Muted, fontSize = 11.5.sp, lineHeight = 16.sp)

            // 앱
            SettingsGroup("앱")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, OloColors.Line, RoundedCornerShape(12.dp))
                .background(OloColors.Surface).clickable(onClick = onAbout).padding(15.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("앱 정보 · 오픈소스 고지", Modifier.weight(1f), color = OloColors.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Icon(Icons.Default.ChevronRight, null, tint = OloColors.Muted)
            }
            Text("OLO Cycle ${BuildConfig.VERSION_NAME}", Modifier.padding(4.dp, 14.dp), color = OloColors.Muted, fontSize = 11.sp)
            Spacer(Modifier.height(20.dp))
        }
    }

    // 내보내기 암호 입력(확인 2회) → 저장 위치 선택기 실행.
    if (passExport) {
        PassphraseDialog(
            title = "백업 암호 설정", confirmLabel = "저장 위치 선택", requireConfirm = true,
            onDismiss = { passExport = false },
            onConfirm = { pass ->
                passExport = false
                exportPassphrase = pass
                val stamp = java.time.LocalDate.now().toString().replace("-", "")
                onExternalPick()
                exportLauncher.launch("olo-cycle-backup-$stamp.olobak")
            },
        )
    }
    // 파일 선택 후 복원 암호 입력 → 덮어쓰기 확인.
    pendingImportUri?.let { uri ->
        PassphraseDialog(
            title = "복원 암호 입력", confirmLabel = "확인", requireConfirm = false,
            onDismiss = { pendingImportUri = null },
            onConfirm = { pass ->
                pendingImportUri = null
                val bytes = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                if (bytes == null) Toast.makeText(ctx, "파일을 읽지 못했습니다.", Toast.LENGTH_LONG).show()
                else confirmRestore = bytes to pass
            },
        )
    }
    confirmRestore?.let { (bytes, pass) ->
        val runImport: (HomeViewModel.ImportMode) -> Unit = { mode ->
            confirmRestore = null
            busy = true
            scope.launch {
                val result = runCatching { onImport(bytes, pass, mode) }
                busy = false
                val msg = result.fold(
                    onSuccess = {
                        if (mode == HomeViewModel.ImportMode.MERGE) "합치기 완료 · 구성원 ${it}명 반영" else "복원 완료 · 구성원 ${it}명"
                    },
                    onFailure = { e ->
                        when (e) {
                            is javax.crypto.AEADBadTagException -> "비밀번호가 올바르지 않거나 파일이 손상되었습니다."
                            is BackupCodec.BadBackupException -> e.message ?: "백업 파일이 아닙니다."
                            else -> "복원에 실패했습니다."
                        }
                    },
                )
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
        }
        OloDialog(
            title = "백업에서 복원", onDismiss = { confirmRestore = null },
            confirmLabel = "취소", onConfirm = { confirmRestore = null }, dismissLabel = null,
        ) {
            Text("복원 방식을 선택하세요.", color = OloColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { runImport(HomeViewModel.ImportMode.MERGE) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = OloColors.Primary),
                shape = RoundedCornerShape(12.dp),
            ) { Text("현재 데이터에 합치기", fontWeight = FontWeight.Bold) }
            Text("기존 데이터는 그대로 두고, 백업의 구성원·기록을 추가합니다(이름이 같은 구성원은 합치고, 같은 날짜 기록은 기존 유지).",
                Modifier.padding(top = 6.dp, bottom = 14.dp), color = OloColors.Muted, fontSize = 11.5.sp, lineHeight = 16.sp)
            OutlinedButton(
                onClick = { runImport(HomeViewModel.ImportMode.REPLACE) },
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, OloColors.Period),
                shape = RoundedCornerShape(12.dp),
            ) { Text("전부 대체 (덮어쓰기)", color = OloColors.Period, fontWeight = FontWeight.Bold) }
            Text("현재 이 기기의 모든 구성원·기록을 지우고 백업 내용으로 바꿉니다. 되돌릴 수 없습니다.",
                Modifier.padding(top = 6.dp), color = OloColors.Muted, fontSize = 11.5.sp, lineHeight = 16.sp)
        }
    }
}

/** 백업 암호 입력창. requireConfirm이면 확인 칸까지 일치해야 진행. 최소 4자. */
@Composable
private fun PassphraseDialog(
    title: String, confirmLabel: String, requireConfirm: Boolean,
    onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val tooShort = pw.length < 4
    val mismatch = requireConfirm && pw != pw2
    val invalid = pw.isBlank() || tooShort || mismatch
    OloDialog(title = title, onDismiss = onDismiss, confirmLabel = confirmLabel,
        onConfirm = { if (!invalid) onConfirm(pw.toCharArray()) }) {
        OutlinedTextField(pw, { pw = it }, label = { Text("비밀번호") }, singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(), colors = oloFieldColors())
        if (requireConfirm) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(pw2, { pw2 = it }, label = { Text("비밀번호 확인") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), colors = oloFieldColors())
        }
        Spacer(Modifier.height(6.dp))
        val hint = when {
            pw.isNotEmpty() && tooShort -> "4자 이상 입력해 주세요."
            mismatch -> "두 비밀번호가 일치하지 않습니다."
            requireConfirm -> "복원할 때 이 비밀번호가 필요합니다. 잊지 마세요."
            else -> "백업을 만들 때 설정한 비밀번호를 입력하세요."
        }
        Text(hint, color = if (tooShort && pw.isNotEmpty() || mismatch) OloColors.Period else OloColors.Muted, fontSize = 11.5.sp)
    }
}

@Composable
private fun SettingsGroup(title: String) {
    Text(title, Modifier.padding(4.dp, 18.dp, 4.dp, 8.dp), color = OloColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
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

/**
 * 앱 공통 다이얼로그 껍데기. 기본 Material AlertDialog는 표면색이 보라(라벤더) 계열로 떠서 OLO 웜톤과
 * 어긋나고, text 슬롯의 높이 제약 때문에 내용이 길면 마지막 입력칸(예: 메모)이 잘린다. 그래서 직접
 * [Dialog] + [Surface]로 구성한다: 제목은 상단 고정, 본문은 남는 높이만큼(최대 화면의 85%) 스크롤,
 * 확인/취소 버튼은 하단 고정 → 어떤 내용이 와도 버튼과 마지막 칸이 항상 보인다. 색은 OLO 토큰만 사용.
 */
@Composable
private fun OloDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = "취소",
    accent: Color = OloColors.Primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = OloColors.Surface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = maxHeight),
        ) {
            Column(Modifier.padding(top = 22.dp, bottom = 10.dp)) {
                Text(title, Modifier.padding(horizontal = 24.dp), color = OloColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(16.dp))
                // 남는 높이만큼만 차지하고(fill=false) 그 안에서 스크롤 → 마지막 칸이 잘리지 않는다.
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    content = content,
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (dismissLabel != null) {
                        TextButton(onClick = onDismiss) { Text(dismissLabel, color = OloColors.Muted, fontWeight = FontWeight.SemiBold) }
                        Spacer(Modifier.width(4.dp))
                    }
                    TextButton(onClick = onConfirm) { Text(confirmLabel, color = accent, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    OloDialog(
        title = "OLO Cycle ${BuildConfig.VERSION_NAME}",
        onDismiss = onDismiss,
        confirmLabel = "확인",
        onConfirm = onDismiss,
        dismissLabel = null,
    ) {
        Text(
            "가족 구성원별로 생리 주기를 따로 관리하는 앱입니다. 예측은 달력법 기반의 참고용 추정치이며 피임·진단의 근거가 아닙니다.\n\n" +
                "· 데이터는 이 기기에만 저장 · 인터넷 권한 없음\n" +
                "· 디자인: OLO 디자인 시스템 (Apache/MIT 오픈소스 기반)",
            color = OloColors.Ink, fontSize = 13.sp, lineHeight = 20.sp,
        )
    }
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

    OloDialog(
        title = if (original == null) "구성원 추가" else "구성원 편집",
        onDismiss = onDismiss,
        confirmLabel = if (original == null) "만들기" else "저장",
        onConfirm = { onSave(name, OloColors.ProfilePalette[colorIndex].toArgb(), cycle, period, birthControl, photoPath) },
    ) {
                // 사진: 있으면 미리보기, 없으면 색+이니셜. 선택/제거 버튼을 옆에.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val previewBmp = rememberProfileBitmap(photoPath)
                    // 홈 상단과 동일한 사각(라운드) 아바타. 이름은 관습대로 2글자.
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(OloColors.ProfilePalette[colorIndex]), contentAlignment = Alignment.Center) {
                        if (previewBmp != null) {
                            Image(previewBmp, "프로필 사진", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(avatarInitials(name).ifBlank { "?" }, color = Color.White, fontWeight = FontWeight.Bold,
                                fontSize = if (avatarInitials(name).length >= 2) 20.sp else 24.sp)
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
                OutlinedTextField(name, { name = it }, label = { Text("이름(별명)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = oloFieldColors())
                Spacer(Modifier.height(12.dp))
                Text("구성원 색상", fontSize = 12.sp, color = OloColors.Muted)
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
                        TextButton(onClick = { confirmDelete = true }) { Text("이 구성원 삭제", color = OloColors.Period) }
                    } else {
                        Text("이 구성원의 모든 기록이 함께 삭제됩니다.", color = OloColors.Period, fontSize = 12.sp)
                        Row {
                            TextButton(onClick = onDelete) { Text("삭제 확인", color = OloColors.Period) }
                            TextButton(onClick = { confirmDelete = false }) { Text("취소") }
                        }
                    }
                }
    }
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

    OloDialog(
        title = "${date.monthValue}월 ${date.dayOfMonth}일 기록",
        onDismiss = onDismiss,
        confirmLabel = "저장",
        onConfirm = {
            onSetPeriodStart(periodStart)
            onSave(flow, symptoms.toList(), mood, temperature.toDoubleOrNull(), memo)
        },
    ) {
        // 생리 시작일 토글: 켜면 예측 기준이 이 날로 갱신된다. 강조 카드로 다른 입력과 구분.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OloColors.AccentContainer)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("생리 시작일", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OloColors.OnAccentContainer)
                Text("켜면 이 날 기준으로 예측이 갱신돼요.", color = OloColors.OnAccentContainer.copy(alpha = 0.75f), fontSize = 11.sp)
            }
            Switch(
                periodStart, { periodStart = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = OloColors.Primary),
            )
        }

        FieldLabel("생리량")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FLOW_LABELS.forEachIndexed { i, label ->
                SelectChip(label, selected = flow == i, color = OloColors.Period) { flow = if (flow == i) null else i }
            }
        }
        FieldLabel("증상")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SYMPTOM_OPTIONS.forEach { s ->
                SelectChip(s, selected = s in symptoms, color = OloColors.Amber) { if (s in symptoms) symptoms.remove(s) else symptoms.add(s) }
            }
        }
        FieldLabel("기분")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            MOOD_OPTIONS.forEach { m ->
                SelectChip(m, selected = mood == m, color = OloColors.Fertile) { mood = if (mood == m) null else m }
            }
        }
        FieldLabel("기초체온 (℃)")
        OutlinedTextField(temperature, { temperature = it }, singleLine = true, placeholder = { Text("예: 36.6") },
            modifier = Modifier.fillMaxWidth(), colors = oloFieldColors())
        FieldLabel("메모")
        OutlinedTextField(memo, { memo = it }, modifier = Modifier.fillMaxWidth(), minLines = 3,
            placeholder = { Text("자유롭게 남겨요") }, colors = oloFieldColors())
        Spacer(Modifier.height(4.dp))
    }
}

/** OutlinedTextField 공통 OLO 색상(기본 Material 보라 대신 클레이 강조·아이보리 배경). */
@Composable
private fun oloFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OloColors.Primary,
    unfocusedBorderColor = OloColors.Line,
    cursorColor = OloColors.Primary,
    focusedTextColor = OloColors.Ink,
    unfocusedTextColor = OloColors.Ink,
    focusedContainerColor = OloColors.Surface,
    unfocusedContainerColor = OloColors.Surface,
)

@Composable
private fun FieldLabel(text: String, dot: Color? = null) {
    Row(Modifier.padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(7.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = OloColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val bg = if (selected) color else OloColors.SurfaceSoft
    val fg = if (selected) Color.White else OloColors.Ink
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, if (selected) color else OloColors.Line, RoundedCornerShape(16.dp))
            .background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

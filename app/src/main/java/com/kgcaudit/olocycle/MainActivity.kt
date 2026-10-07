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

internal enum class Tab(val label: String, val icon: ImageVector) {
    HOME("오늘", OloIcons.Home),
    CALENDAR("달력", OloIcons.Calendar),
    ANALYSIS("분석", OloIcons.Chart),
}

@Composable
internal fun App(vm: HomeViewModel = viewModel()) {
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
    var lastIndex by remember { mutableIntStateOf(-1) }
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

    // 반응형: 폭 600dp 이상(폴더블 펼침·태블릿)이면 왼쪽 세로 레일, 미만(휴대폰·커버)이면 하단 바.
    BoxWithConstraints(Modifier.fillMaxSize().background(OloColors.Background)) {
        val expanded = maxWidth >= 600.dp
        ResponsiveNav(
            expanded = expanded,
            tab = tab,
            accent = profileColor,
            onSelect = { tab = it },
            onFab = { recordDate = state.today },
            header = {
                // 화면별 보조 맥락줄: 오늘=날짜·요일 / 달력=보이는 월 / 분석=프로필 리포트.
                val tabSubtitle = when (tab) {
                    Tab.HOME -> {
                        val d = state.today
                        val dow = KO_DOW[d.dayOfWeek.value % 7]
                        "${d.monthValue}월 ${d.dayOfMonth}일 ${dow}요일"
                    }
                    Tab.CALENDAR -> "${visibleMonth.year}년 ${visibleMonth.monthValue}월"
                    Tab.ANALYSIS -> state.selected?.name?.let { "$it · 주기 리포트" }
                }
                MemberSwitcher(
                    tabLabel = tab.label,
                    profiles = state.profiles,
                    selectedId = state.selected?.id,
                    onSelect = vm::select,
                    onAdd = { showAdd = true },
                    onEditCurrent = { state.selected?.let { editProfile = it } },
                    onSettings = { showSettings = true },
                    tabSubtitle = tabSubtitle,
                )
            },
        ) { contentModifier ->
            ScreenContent(
                modifier = contentModifier,
                state = state, profileColor = profileColor, tab = tab, expanded = expanded,
                contentOffset = contentOffset.value, contentAlpha = contentAlpha.value,
                visibleMonth = visibleMonth, onSetMonth = { visibleMonth = it },
                onSwitchMember = switchMember,
                onGoCalendar = { tab = Tab.CALENDAR },
                onDayClick = { recordDate = it },
                onSetPeriodStart = { d, on -> vm.setPeriodStart(d, on) },
                onSaveRecord = { d, flow, sym, mood, temp, memo -> vm.saveDayRecord(d, flow, sym, mood, temp, memo) },
            )
        }
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

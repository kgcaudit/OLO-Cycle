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


// ---------------------------------------------------------------------------- top member switcher + bottom bar

/**
 * 최상단 고정 구성원 스위처(모든 화면 공통 앵커) — 한 줄 사각형 바.
 * 제목 오른쪽에 사각 타일: 활성은 색 배경+2글자 이름으로 확장, 비활성은 사각 아바타 아이콘만. ＋추가, ✎편집, ⚙설정.
 */
@Composable
internal fun MemberSwitcher(
    tabLabel: String, profiles: List<Profile>, selectedId: Long?,
    onSelect: (Long) -> Unit, onAdd: () -> Unit, onEditCurrent: () -> Unit, onSettings: () -> Unit,
    tabSubtitle: String? = null,
) {
    Column(Modifier.fillMaxWidth().background(OloColors.Surface).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 8.dp, 4.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 화면명이 페이지 주 타이틀. 워드마크(OLO Cycle 글자)는 뺐고, 여성 기호 ♀ 만 브랜드 흔적으로 남긴다.
            OloRingMark(size = 22.dp, color = OloColors.Primary)
            Spacer(Modifier.width(10.dp))
            // 화면명 + 그 아래 보조 맥락줄(오늘=날짜·요일 / 달력=해당 월 / 분석=프로필·리포트).
            Column {
                Text(tabLabel, color = OloColors.Ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 20.sp, maxLines = 1)
                if (tabSubtitle != null) {
                    Text(tabSubtitle, color = OloColors.Muted, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1)
                }
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
                            Icon(OloIcons.Edit, "이 구성원 편집", tint = OloColors.Muted, modifier = Modifier.size(13.dp))
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
                ) { Icon(OloIcons.Add, "구성원 추가", tint = OloColors.Outline, modifier = Modifier.size(18.dp)) }
            }
            IconButton(onSettings, Modifier.size(38.dp)) { Icon(OloIcons.Settings, "설정", tint = OloColors.Muted, modifier = Modifier.size(19.dp)) }
        }
        HorizontalDivider(color = OloColors.Line)
    }
}

/** 앱 상징 마크 — 여성 기호 ♀(위 원 + 아래 세로획 + 가로 십자획). 클레이 단색, 작은 크기에서도 또렷하도록 굵은 획. */
@Composable
internal fun OloRingMark(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val r = s * 0.265f               // 원(머리) 반지름
        val sw = s * 0.125f              // 획 두께(또렷)
        val stem = r * 1.02f             // 원 아래 세로획 길이
        val crossHalf = r * 0.56f        // 가로 십자획 반폭
        val cx = s * 0.5f
        // 글리프 전체(2R + stem)를 세로 가운데 맞춤.
        val top = (s - (2f * r + stem)) / 2f
        val ringCy = top + r
        val stemTop = ringCy + r
        val stemBottom = stemTop + stem
        val crossY = stemTop + stem * 0.52f
        drawCircle(color, radius = r, center = Offset(cx, ringCy), style = Stroke(sw))
        drawLine(color, Offset(cx, stemTop), Offset(cx, stemBottom), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(cx - crossHalf, crossY), Offset(cx + crossHalf, crossY), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** 하단 3탭 + 오른쪽 ＋기록. 활성 탭·FAB는 활성 구성원 색(B안 강조). */
@Composable
internal fun BottomBar(current: Tab, accent: Color, onSelect: (Tab) -> Unit, onFab: () -> Unit) {
    // 3개 뷰 탭(오늘·달력·분석) + 오른쪽 끝에 기록 추가 ＋ 하나. 바 안에 두어 콘텐츠와 겹치지 않는다.
    Column(Modifier.testTag("bottombar")) {
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
                    ) { Icon(OloIcons.Add, "기록 추가", tint = Color.White, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.height(2.dp))
                    Text("기록", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun NavItem(modifier: Modifier, tab: Tab, current: Tab, accent: Color, onSelect: (Tab) -> Unit) {
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
 * 반응형 뼈대: 상단 공통 헤더 아래, 넓은 화면이면 [왼쪽 세로 레일 + 가운데 본문(읽기 좋은 최대폭)],
 * 좁은 화면이면 [본문 + 하단 바]. 본문은 두 모드가 같은 화면을 그대로 재사용한다(content 슬롯).
 */
@Composable
internal fun ResponsiveNav(
    expanded: Boolean,
    tab: Tab,
    accent: Color,
    onSelect: (Tab) -> Unit,
    onFab: () -> Unit,
    header: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        header()
        if (expanded) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                SideRail(tab, accent, onSelect, onFab)
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    content(Modifier.widthIn(max = 600.dp).fillMaxSize())
                }
            }
        } else {
            content(Modifier.weight(1f).fillMaxWidth())
            BottomBar(tab, accent, onSelect, onFab)
        }
    }
}

/** 넓은 화면용 왼쪽 세로 내비게이션 레일 — 하단 바와 같은 항목(오늘·달력·분석 + 기록). */
@Composable
internal fun SideRail(current: Tab, accent: Color, onSelect: (Tab) -> Unit, onFab: () -> Unit) {
    Row(Modifier.fillMaxHeight().testTag("rail")) {
        Column(
            Modifier.fillMaxHeight().width(88.dp).background(OloColors.Surface).navigationBarsPadding().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            RailItem(Tab.HOME, current, accent, onSelect)
            RailItem(Tab.CALENDAR, current, accent, onSelect)
            RailItem(Tab.ANALYSIS, current, accent, onSelect)
            Spacer(Modifier.height(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(50.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(accent).clickable(onClick = onFab),
                    contentAlignment = Alignment.Center,
                ) { Icon(OloIcons.Add, "기록 추가", tint = Color.White, modifier = Modifier.size(26.dp)) }
                Spacer(Modifier.height(3.dp))
                Text("기록", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
            }
        }
        VerticalDivider(color = OloColors.Line)
    }
}

@Composable
internal fun RailItem(tab: Tab, current: Tab, accent: Color, onSelect: (Tab) -> Unit) {
    val on = tab == current
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable { onSelect(tab) }.padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(56.dp).height(32.dp).clip(RoundedCornerShape(16.dp))
                .background(if (on) accent.copy(alpha = 0.16f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { Icon(tab.icon, tab.label, tint = if (on) accent else OloColors.Muted, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(3.dp))
        Text(tab.label, fontSize = 11.sp, color = if (on) accent else OloColors.Muted,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

/** 본문(홈·달력·분석) — 좌우 스와이프로 구성원 이동, 구성원 전환 슬라이드/페이드. 두 모드 공통. */
@Composable
internal fun ScreenContent(
    modifier: Modifier,
    state: HomeState,
    profileColor: Color,
    tab: Tab,
    expanded: Boolean,
    contentOffset: Float,
    contentAlpha: Float,
    visibleMonth: YearMonth,
    onSetMonth: (YearMonth) -> Unit,
    onSwitchMember: (Int) -> Unit,
    onGoCalendar: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onSetPeriodStart: (LocalDate, Boolean) -> Unit = { _, _ -> },
    onSaveRecord: (LocalDate, Int?, List<String>, String?, Double?, String?) -> Unit = { _, _, _, _, _, _ -> },
) {
    Box(
        modifier.pointerInput(state.profiles, state.selected?.id) {
            val threshold = 56.dp.toPx()
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    if (total <= -threshold) onSwitchMember(+1)      // ← 다음 구성원
                    else if (total >= threshold) onSwitchMember(-1)  // → 이전 구성원
                },
            ) { change, dragAmount -> total += dragAmount; change.consume() }
        },
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = contentOffset; alpha = contentAlpha }) {
            when (tab) {
                Tab.HOME -> HomeDashboard(state, profileColor, expanded, onOpenCalendar = onGoCalendar)
                Tab.CALENDAR -> CalendarTab(state, profileColor, visibleMonth, expanded, onSetMonth = onSetMonth,
                    onDayClick = onDayClick, onSetPeriodStart = onSetPeriodStart, onSaveRecord = onSaveRecord)
                Tab.ANALYSIS -> AnalysisTab(state, profileColor, expanded, onDayClick)
            }
        }
    }
}

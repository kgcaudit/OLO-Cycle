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


/**
 * 사각 아바타에 넣을 글자: 최소 2자. 한국 성명 관습(성1+이름2, 남궁·황보 등 복성)에서 이름은 보통 끝 2자이므로
 * 3자 이상이면 끝 2자(이름), 2자 이하면 그대로. (예: 김하늘→"하늘", 남궁민수→"민수", 김철→"김철", 정→"정")
 */
internal fun avatarInitials(name: String): String {
    val n = name.trim()
    return when {
        n.isEmpty() -> "?"
        n.length <= 2 -> n
        else -> n.takeLast(2)
    }
}

/** Decodes a profile photo path into an [ImageBitmap], cached per path. Null path/decode → colour+initial. */
@Composable
internal fun rememberProfileBitmap(path: String?): ImageBitmap? =
    remember(path) { path?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() } }

/**
 * 프로필 아바타: 사진이 있으면 사진, 없으면 프로필 색 + 이름 이니셜. 사진·색은 프로필 고유 정체성이라
 * 헤더·목록·편집창에서 같은 규칙으로 보여 준다.
 */
@Composable
internal fun ProfileAvatar(
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
internal fun AppLockGate(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(OloColors.Background).padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(OloColors.AccentContainer), contentAlignment = Alignment.Center) {
            Icon(OloIcons.Lock, null, tint = OloColors.Primary, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("OLO Cycle 잠금", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OloColors.Ink)
        Spacer(Modifier.height(4.dp))
        Text("생체인증 또는 화면 잠금으로 확인하세요.", color = OloColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onUnlock, colors = ButtonDefaults.buttonColors(containerColor = OloColors.Primary), shape = OloButtonShape) {
            Icon(OloIcons.Lock, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("잠금 해제", fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------------------- home (dashboard)

/** 홈 = 오늘: 상태 히어로 · 다가오는 일정 · 이번 주. 기록 추가는 하단 ＋ 로 통일(중복 버튼 없음). */
@Composable
internal fun HomeDashboard(
    state: HomeState, profileColor: Color, expanded: Boolean, onOpenCalendar: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(14.dp))
        HeroCard(state, profileColor)
        Spacer(Modifier.height(12.dp))
        if (expanded) {
            // 태블릿: 히어로 아래 [다가오는 일정 | 이번 주] 2열로 넓이를 살린다.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { UpcomingCard(state, profileColor) }
                Box(Modifier.weight(1f)) { WeekStrip(state, profileColor, onOpenCalendar) }
            }
        } else {
            UpcomingCard(state, profileColor)
            Spacer(Modifier.height(12.dp))
            WeekStrip(state, profileColor, onOpenCalendar)
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 왼쪽에 구성원 색 세로 라인을 둔 카드 — "지금 누구의 내용인지" 한눈에 식별. */
@Composable
internal fun AccentCard(accent: Color, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(14.dp))
            .background(OloColors.Surface).border(1.dp, OloColors.Line, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
        Column(Modifier.weight(1f).padding(15.dp, 13.dp), content = content)
    }
}

/**
 * 상태 히어로 카드: 구성원 색 그라데이션 + 단계·D-day·다음 예정 + 주기 진행 바.
 * 진행을 히어로 안에 통합해 하단 별도 카드와의 중복을 없앤다(원형 링 대신 선형 바로 숫자까지 함께 보여줌).
 */
@Composable
internal fun HeroCard(state: HomeState, profileColor: Color) {
    val d = state.daysUntilNextPeriod
    val cycle = state.params?.cycleLength ?: 28
    Box(
        // 고정 높이(clip)를 쓰면 큰 글꼴 기기에서 마지막 줄이 잘린다 → 최소 높이로 두어 내용에 맞게 늘어나게.
        Modifier.fillMaxWidth().heightIn(min = 140.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(lerp(profileColor, Color.White, 0.12f), lerp(profileColor, Color.Black, 0.16f))))
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("● ${phaseName(state.currentPhase())}" + (state.cycleDayIndex?.let { " · 주기 ${it}일째" } ?: ""),
                color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(when { d == null -> "기록 전"; d >= 0 -> "D-$d"; else -> "D+${-d}" },
                color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 48.sp)
            Text(state.prediction?.let { "다음 생리 ${it.nextPeriodStart.monthValue}/${it.nextPeriodStart.dayOfMonth} 예정" }
                ?: "생리 시작일을 기록해 보세요", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1)
            // 주기 진행 바(기록이 있을 때만). 링을 대체한다.
            state.cycleDayIndex?.let { idx ->
                Spacer(Modifier.height(6.dp))
                val frac = (idx.toFloat() / cycle).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.28f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(5.dp)).background(Color.White))
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("${idx}일째", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${cycle}일 주기", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
                }
            }
        }
    }
}

/** 다가오는 일정: 다음 생리·배란·가임기와 각 D-day. 좌측 구성원 색 라인. */
@Composable
internal fun UpcomingCard(state: HomeState, profileColor: Color) {
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
internal fun UpcomingRow(color: Color, label: String, date: LocalDate, today: LocalDate, trailing: String? = null) {
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
internal fun WeekStrip(state: HomeState, profileColor: Color, onOpenCalendar: () -> Unit) {
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
            val labels = KO_DOW
            (0..6).forEach { i ->
                val day = sunday.plusDays(i.toLong())
                val (bg, fg) = phaseColors(state.phaseOf(day))
                val isToday = day == today
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg).padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(labels[i], fontSize = 9.sp, color = if (bg == Color.White) OloColors.Muted else fg)
                    Spacer(Modifier.height(3.dp))
                    // 숫자 영역은 고정 높이 슬롯 → 모든 칸 높이가 같다. 오늘만 그 안에 구성원색 원(강조는 색으로).
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (isToday) Box(Modifier.matchParentSize().clip(CircleShape).background(profileColor))
                        Text("${day.dayOfMonth}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (isToday) Color.White else fg)
                    }
                }
            }
        }
    }
}

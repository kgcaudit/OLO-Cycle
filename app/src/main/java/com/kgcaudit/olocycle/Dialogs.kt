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
 * 앱 공통 다이얼로그 껍데기. 기본 Material AlertDialog는 표면색이 보라(라벤더) 계열로 떠서 OLO 웜톤과
 * 어긋나고, text 슬롯의 높이 제약 때문에 내용이 길면 마지막 입력칸(예: 메모)이 잘린다. 그래서 직접
 * [Dialog] + [Surface]로 구성한다: 제목은 상단 고정, 본문은 남는 높이만큼(최대 화면의 85%) 스크롤,
 * 확인/취소 버튼은 하단 고정 → 어떤 내용이 와도 버튼과 마지막 칸이 항상 보인다. 색은 OLO 토큰만 사용.
 */
@Composable
internal fun OloDialog(
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
            // 큰 화면(태블릿·가로)에서 다이얼로그가 과도하게 넓어지지 않게 최대폭을 제한한다(폰은 0.92f 그대로).
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 420.dp).heightIn(max = maxHeight),
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
                        TextButton(onClick = onDismiss, shape = OloButtonShape) { Text(dismissLabel, color = OloColors.Muted, fontWeight = FontWeight.SemiBold) }
                        Spacer(Modifier.width(4.dp))
                    }
                    TextButton(onClick = onConfirm, shape = OloButtonShape) { Text(confirmLabel, color = accent, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
internal fun AboutDialog(onDismiss: () -> Unit) {
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
internal fun ProfileEditorDialog(
    original: Profile?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Int, cycle: Int, period: Int, birthControl: Boolean, photoPath: String?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var colorIndex by remember {
        mutableIntStateOf(original?.let { p -> OloColors.ProfilePalette.indexOfFirst { it.toArgb() == p.color }.coerceAtLeast(0) } ?: 0)
    }
    var cycle by remember { mutableIntStateOf(original?.defaultCycleLength ?: 28) }
    var period by remember { mutableIntStateOf(original?.defaultPeriodLength ?: 5) }
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
                        }, shape = OloButtonShape) { Text(if (photoPath == null) "사진 추가" else "사진 변경") }
                        if (photoPath != null) {
                            TextButton(onClick = {
                                if (photoPath != original?.photoPath) ProfilePhotos.delete(photoPath) // 저장 전 새로 만든 파일이면 정리
                                photoPath = null
                            }, shape = OloButtonShape) { Text("사진 제거", color = OloColors.Muted) }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stepper(Modifier.weight(1f), "평균 주기", cycle, 15, 60, "일") { cycle = it }
                    Stepper(Modifier.weight(1f), "생리 기간", period, 1, 10, "일") { period = it }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("피임약 복용", Modifier.weight(1f), fontSize = 13.sp); Switch(birthControl, { birthControl = it })
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    if (!confirmDelete) {
                        TextButton(onClick = { confirmDelete = true }, shape = OloButtonShape) { Text("이 구성원 삭제", color = OloColors.Period) }
                    } else {
                        Text("이 구성원의 모든 기록이 함께 삭제됩니다.", color = OloColors.Period, fontSize = 12.sp)
                        Row {
                            TextButton(onClick = onDelete, shape = OloButtonShape) { Text("삭제 확인", color = OloColors.Period) }
                            TextButton(onClick = { confirmDelete = false }, shape = OloButtonShape) { Text("취소") }
                        }
                    }
                }
    }
}

/** 컴팩트 조절바: 라벨(위) + [− 값단위 +] pill. 한 줄에 둘을 나란히 둘 수 있어 세로 공간을 아낀다. */
@Composable
internal fun Stepper(modifier: Modifier, label: String, value: Int, min: Int, max: Int, unit: String, onChange: (Int) -> Unit) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = OloColors.Muted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(OloColors.SurfaceSoft)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable { if (value > min) onChange(value - 1) },
                contentAlignment = Alignment.Center) { Text("−", fontSize = 18.sp, color = OloColors.Primary) }
            Text("$value$unit", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = OloColors.Ink, fontSize = 15.sp)
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable { if (value < max) onChange(value + 1) },
                contentAlignment = Alignment.Center) { Text("+", fontSize = 18.sp, color = OloColors.Primary) }
        }
    }
}

/** 한글 요일(일~토). dayOfWeek.value(월=1..일=7) 는 `% 7` 로 이 리스트에 바로 매핑된다. */
internal val KO_DOW = listOf("일", "월", "화", "수", "목", "금", "토")
internal val FLOW_LABELS = listOf("없음", "적음", "보통", "많음")
internal val SYMPTOM_OPTIONS = listOf("복통", "두통", "허리통증", "부종", "여드름", "피로", "메스꺼움", "유방통")
internal val MOOD_OPTIONS = listOf("좋음", "평온", "예민", "우울", "불안")

/** 그날 기록 편집 상태 — 다이얼로그(휴대폰)와 인라인 패널(태블릿)이 같은 입력을 공유하도록 홀더로 뺐다. */
internal class DayRecordEditState(isPeriodStart: Boolean, isPeriodEnd: Boolean, existing: com.kgcaudit.olocycle.data.DayRecord?) {
    var periodStart by mutableStateOf(isPeriodStart)
    var periodEnd by mutableStateOf(isPeriodEnd)
    var flow by mutableStateOf(existing?.flow)
    val symptoms = mutableStateListOf<String>().apply {
        existing?.symptoms?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { addAll(it) }
    }
    var mood by mutableStateOf(existing?.mood)
    var temperature by mutableStateOf(existing?.temperature?.toString() ?: "")
    var weight by mutableStateOf(existing?.weight?.toString() ?: "")
    var memo by mutableStateOf(existing?.memo ?: "")
}

@Composable
internal fun rememberDayRecordEditState(date: LocalDate, isPeriodStart: Boolean, isPeriodEnd: Boolean, existing: com.kgcaudit.olocycle.data.DayRecord?) =
    remember(date, isPeriodStart, isPeriodEnd, existing) { DayRecordEditState(isPeriodStart, isPeriodEnd, existing) }

/** 그날 기록 입력 필드(생리 시작일·생리량·증상·기분·체온·메모). 다이얼로그와 인라인 패널 공용. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayRecordFields(st: DayRecordEditState) {
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
            st.periodStart, { st.periodStart = it },
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = OloColors.Primary),
        )
    }
    Spacer(Modifier.height(8.dp))
    // 생리 종료일 토글: 켜면 이 날을 생리 마지막 날로 기록해 실제 생리 기간(히스토리)에 반영한다.
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OloColors.SurfaceSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("생리 종료일", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OloColors.Ink)
            Text("켜면 이 날을 생리 마지막 날로 기록해요.", color = OloColors.Muted, fontSize = 11.sp)
        }
        Switch(
            st.periodEnd, { st.periodEnd = it },
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = OloColors.Period),
        )
    }

    FieldLabel("생리량")
    FlowDropSelector(selected = st.flow) { st.flow = if (st.flow == it) null else it }
    FieldLabel("증상")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        SYMPTOM_OPTIONS.forEach { s ->
            SelectChip(s, selected = s in st.symptoms, color = OloColors.Amber) { if (s in st.symptoms) st.symptoms.remove(s) else st.symptoms.add(s) }
        }
    }
    FieldLabel("기분")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        MOOD_OPTIONS.forEach { m ->
            SelectChip(m, selected = st.mood == m, color = OloColors.Fertile) { st.mood = if (st.mood == m) null else m }
        }
    }
    FieldLabel("기초체온 (℃)")
    OutlinedTextField(st.temperature, { st.temperature = it }, singleLine = true, placeholder = { Text("예: 36.6") },
        modifier = Modifier.fillMaxWidth(), colors = oloFieldColors())
    FieldLabel("체중 (kg)")
    OutlinedTextField(st.weight, { st.weight = it }, singleLine = true, placeholder = { Text("예: 55.2") },
        modifier = Modifier.fillMaxWidth(), colors = oloFieldColors())
    FieldLabel("메모")
    OutlinedTextField(st.memo, { st.memo = it }, modifier = Modifier.fillMaxWidth(), minLines = 3,
        placeholder = { Text("자유롭게 남겨요") }, colors = oloFieldColors())
}

@Composable
internal fun DayRecordDialog(
    date: LocalDate,
    isPeriodStart: Boolean,
    isPeriodEnd: Boolean,
    existing: com.kgcaudit.olocycle.data.DayRecord?,
    onDismiss: () -> Unit,
    onSetPeriodStart: (Boolean) -> Unit,
    onSetPeriodEnd: (Boolean) -> Unit,
    onSave: (flow: Int?, symptoms: List<String>, mood: String?, temperature: Double?, weight: Double?, memo: String?) -> Unit,
) {
    val st = rememberDayRecordEditState(date, isPeriodStart, isPeriodEnd, existing)
    OloDialog(
        title = "${date.monthValue}월 ${date.dayOfMonth}일 기록",
        onDismiss = onDismiss,
        confirmLabel = "저장",
        onConfirm = {
            onSetPeriodStart(st.periodStart)
            onSetPeriodEnd(st.periodEnd)
            onSave(st.flow, st.symptoms.toList(), st.mood, st.temperature.toDoubleOrNull(), st.weight.toDoubleOrNull(), st.memo)
        },
    ) {
        DayRecordFields(st)
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * 태블릿 마스터-디테일용 인라인 기록 패널 — 달력 오른쪽에서 선택한 날을 다이얼로그 없이 바로 편집.
 * 날짜가 없으면 안내만 보인다. 저장을 누르면 생리 시작일 갱신 + 기록 저장을 한 번에 한다.
 */
@Composable
internal fun DayDetailPanel(
    modifier: Modifier,
    date: LocalDate?,
    isPeriodStart: Boolean,
    isPeriodEnd: Boolean,
    existing: com.kgcaudit.olocycle.data.DayRecord?,
    onSave: (LocalDate, Boolean, Boolean, Int?, List<String>, String?, Double?, Double?, String?) -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, OloColors.Line, RoundedCornerShape(16.dp)).background(OloColors.Surface),
    ) {
        if (date == null) {
            Box(Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("날짜를 선택하면\n여기서 바로 기록해요", color = OloColors.Muted, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
            return
        }
        val st = rememberDayRecordEditState(date, isPeriodStart, isPeriodEnd, existing)
        Row(Modifier.fillMaxWidth().padding(16.dp, 14.dp, 12.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${date.monthValue}월 ${date.dayOfMonth}일 기록", Modifier.weight(1f),
                color = OloColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Button(
                onClick = { onSave(date, st.periodStart, st.periodEnd, st.flow, st.symptoms.toList(), st.mood, st.temperature.toDoubleOrNull(), st.weight.toDoubleOrNull(), st.memo) },
                colors = ButtonDefaults.buttonColors(containerColor = OloColors.Primary),
                shape = OloButtonShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) { Text("저장", color = Color.White, fontWeight = FontWeight.Bold) }
        }
        HorizontalDivider(color = OloColors.Line)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 16.dp)) {
            DayRecordFields(st)
        }
    }
}

/** OutlinedTextField 공통 OLO 색상(기본 Material 보라 대신 클레이 강조·아이보리 배경). */
@Composable
internal fun oloFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OloColors.Primary,
    unfocusedBorderColor = OloColors.Line,
    cursorColor = OloColors.Primary,
    focusedTextColor = OloColors.Ink,
    unfocusedTextColor = OloColors.Ink,
    focusedContainerColor = OloColors.Surface,
    unfocusedContainerColor = OloColors.Surface,
)

@Composable
internal fun FieldLabel(text: String, dot: Color? = null) {
    Row(Modifier.padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(7.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = OloColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SelectChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val bg = if (selected) color else OloColors.SurfaceSoft
    val fg = if (selected) Color.White else OloColors.Ink
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, if (selected) color else OloColors.Line, RoundedCornerShape(16.dp))
            .background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

/**
 * 생리량 4단계 선택기(없음·적음·보통·많음). 텍스트 칩 대신 물방울 농도로 양을 시각화한다
 * (없음=빈 원, 1~3=물방울 개수/진하기). 선택 시 로즈 강조.
 */
@Composable
internal fun FlowDropSelector(selected: Int?, onSelect: (Int) -> Unit) {
    val period = OloColors.Period
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        FLOW_LABELS.forEachIndexed { i, label ->
            val on = selected == i
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .border(1.dp, if (on) period else OloColors.Line, RoundedCornerShape(14.dp))
                    .background(if (on) period.copy(alpha = 0.12f) else OloColors.SurfaceSoft)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) {
                    if (i == 0) {
                        Box(Modifier.size(9.dp).clip(CircleShape).border(1.5.dp, OloColors.Outline, CircleShape))
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) { repeat(i) { FlowDrop(i) } }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(label, fontSize = 11.sp, color = if (on) period else OloColors.Ink, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

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


// ---------------------------------------------------------------------------- settings tab

@Composable
internal fun SettingsScreen(
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
        // 큰 화면(태블릿 ≥600dp)에서 설정 행이 지나치게 길어지지 않게 본문 폭을 제한하고 가운데 정렬한다.
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
            // 보안
            SettingsGroup("보안")
            SettingsGroupCard {
                SettingsIconRow(OloIcons.Lock, "앱 잠금", "열 때 생체인증·PIN") {
                    Switch(appLockEnabled, onToggleAppLock)
                }
            }

            // 개인정보 — 방패 아이콘 + 요약. (전역 항목이라 브랜드 클레이 톤)
            SettingsGroup("개인정보")
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = OloColors.AccentContainer)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
                    Icon(OloIcons.Shield, null, tint = OloColors.OnAccentContainer, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("내 데이터는 이 기기에만 있습니다", color = OloColors.OnAccentContainer, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(6.dp))
                        listOf("인터넷 권한 없음 · 서버 전송 없음", "추적·광고 SDK 없음", "계정 없이 사용").forEach {
                            Text("· $it", color = OloColors.OnAccentContainer, fontSize = 12.5.sp, lineHeight = 19.sp)
                        }
                    }
                }
            }

            // 백업 — 암호화 파일로 내보내고, 그 파일에서 복원. 인터넷 없이 기기 파일로만.
            SettingsGroup("백업")
            SettingsGroupCard {
                SettingsIconRow(OloIcons.Export, "암호화 백업 내보내기", "암호로 잠근 파일로 저장",
                    onClick = if (!busy) ({ passExport = true }) else null) { Icon(Icons.Default.ChevronRight, null, tint = OloColors.Muted) }
                HorizontalDivider(color = OloColors.Line, modifier = Modifier.padding(start = 58.dp))
                SettingsIconRow(OloIcons.Import, "백업에서 복원", "백업 내용으로 되돌리기",
                    onClick = if (!busy) ({ onExternalPick(); importLauncher.launch(arrayOf("*/*")) }) else null) { Icon(Icons.Default.ChevronRight, null, tint = OloColors.Muted) }
            }
            Text("비밀번호를 잊으면 백업을 열 수 없습니다(복구 불가).",
                Modifier.padding(4.dp, 8.dp), color = OloColors.Muted, fontSize = 11.5.sp, lineHeight = 16.sp)

            // 앱
            SettingsGroup("앱")
            SettingsGroupCard {
                SettingsIconRow(Icons.Default.Info, "앱 정보 · 오픈소스 고지", "버전 ${BuildConfig.VERSION_NAME} · 오픈소스 라이선스",
                    onClick = onAbout) { Icon(Icons.Default.ChevronRight, null, tint = OloColors.Muted) }
            }
            Spacer(Modifier.height(24.dp))
          }
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
                shape = OloButtonShape,
            ) { Text("현재 데이터에 합치기", fontWeight = FontWeight.Bold) }
            Text("기존 데이터는 그대로 두고, 백업의 구성원·기록을 추가합니다(이름이 같은 구성원은 합치고, 같은 날짜 기록은 기존 유지).",
                Modifier.padding(top = 6.dp, bottom = 14.dp), color = OloColors.Muted, fontSize = 11.5.sp, lineHeight = 16.sp)
            OutlinedButton(
                onClick = { runImport(HomeViewModel.ImportMode.REPLACE) },
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, OloColors.Period),
                shape = OloButtonShape,
            ) { Text("전부 대체 (덮어쓰기)", color = OloColors.Period, fontWeight = FontWeight.Bold) }
            Text("현재 이 기기의 모든 구성원·기록을 지우고 백업 내용으로 바꿉니다. 되돌릴 수 없습니다.",
                Modifier.padding(top = 6.dp), color = OloColors.Muted, fontSize = 11.5.sp, lineHeight = 16.sp)
        }
    }
}

/** 백업 암호 입력창. requireConfirm이면 확인 칸까지 일치해야 진행. 최소 4자. */
@Composable
internal fun PassphraseDialog(
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
internal fun SettingsGroup(title: String) {
    Text(title, Modifier.padding(4.dp, 18.dp, 4.dp, 8.dp), color = OloColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
}

// 설정 그룹 카드: 둥근 테두리로 묶어 한 그룹의 항목들을 담는다(나안 B, 아이콘 리스트형).
@Composable
internal fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .border(1.dp, OloColors.Line, RoundedCornerShape(14.dp)).background(OloColors.Surface),
        content = content,
    )
}

// 설정 항목 행: 왼쪽 클레이 원형 아이콘 + 제목·부제, 오른쪽 trailing(스위치·화살표). onClick 이 있으면 눌러진다.
@Composable
internal fun SettingsIconRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val base = Modifier.fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(14.dp, 9.dp)
    Row(base, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(OloColors.Primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = OloColors.Primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = OloColors.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = OloColors.Muted, fontSize = 12.sp)
        }
        trailing()
    }
}

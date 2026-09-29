package com.kgcaudit.olocycle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kgcaudit.olocycle.cycle.CycleParams
import com.kgcaudit.olocycle.cycle.CyclePrediction
import com.kgcaudit.olocycle.cycle.CyclePredictor
import com.kgcaudit.olocycle.cycle.Phase
import com.kgcaudit.olocycle.data.BackupCodec
import com.kgcaudit.olocycle.data.DayRecord
import com.kgcaudit.olocycle.data.OloDatabase
import com.kgcaudit.olocycle.data.PeriodStart
import com.kgcaudit.olocycle.data.Profile
import com.kgcaudit.olocycle.data.ProfilePhotos
import androidx.compose.ui.graphics.toArgb
import com.kgcaudit.olocycle.ui.theme.OloColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** UI state for the home screen: the selected member, their prediction, and the phase per day. */
data class HomeState(
    val profiles: List<Profile> = emptyList(),
    val selected: Profile? = null,
    val periodStarts: List<LocalDate> = emptyList(),
    val dayRecords: Map<LocalDate, DayRecord> = emptyMap(),
    val prediction: CyclePrediction? = null,
    val daysUntilNextPeriod: Int? = null,
    val params: CycleParams? = null,
    /** 1-based day of the current cycle (오늘이 주기 며칠째), or null with no history. */
    val cycleDayIndex: Int? = null,
    /** Recent cycle lengths (days between starts), oldest→newest, for stats. */
    val recentCycleLengths: List<Int> = emptyList(),
    val today: LocalDate = LocalDate.now(),
) {
    /**
     * Phase for [day], from all recorded starts + projection, so every recorded period colours the
     * calendar (not only the current cycle).
     */
    fun phaseOf(day: LocalDate): Phase =
        params?.let { CyclePredictor.phaseForDay(day, periodStarts, it) } ?: Phase.UNKNOWN

    fun currentPhase(): Phase = phaseOf(today)

    fun isPeriodStart(day: LocalDate): Boolean = day in periodStarts

    /** 1-based day within its recorded period (생리 며칠째), or null when [day] is not a recorded period day. */
    fun periodDayNumber(day: LocalDate): Int? {
        val len = params?.periodLength ?: return null
        val start = periodStarts
            .filter { !it.isAfter(day) && day.isBefore(it.plusDays(len.toLong())) }
            .maxOrNull() ?: return null
        return (day.toEpochDay() - start.toEpochDay() + 1).toInt()
    }

    /** 달력 칸에 얹을 짧은 라벨: 생리 진행 일수 / 예정 / 배란. (가임은 색으로 충분해 라벨 생략) */
    fun calendarCellLabel(day: LocalDate): String? = when (phaseOf(day)) {
        Phase.PERIOD -> periodDayNumber(day)?.let { "${it}일" }
        Phase.PREDICTED_PERIOD -> "예정"
        Phase.OVULATION -> "배란"
        else -> null
    }

    /**
     * 주기 히스토리(최근 것부터): 각 기록 생리 시작일 → 예상 종료일(시작+생리기간−1)과 다음 시작까지의 주기 길이.
     * 마지막(가장 최근) 항목은 다음 시작이 없으므로 주기 길이 null.
     */
    fun periodHistory(): List<CycleHistoryItem> {
        val len = params?.periodLength ?: 5
        val sorted = periodStarts.distinct().sorted()
        return sorted.mapIndexed { i, start ->
            val next = sorted.getOrNull(i + 1)
            CycleHistoryItem(
                start = start,
                end = start.plusDays((len - 1).toLong()),
                cycleLength = next?.let { (it.toEpochDay() - start.toEpochDay()).toInt() },
            )
        }.reversed()
    }

    fun recordOf(day: LocalDate): DayRecord? = dayRecords[day]

    /** True when the day carries any log entry the calendar should mark with a dot. */
    fun hasNote(day: LocalDate): Boolean = dayRecords[day]?.let {
        it.flow != null || !it.symptoms.isNullOrBlank() || !it.mood.isNullOrBlank() ||
            it.temperature != null || !it.memo.isNullOrBlank()
    } ?: false

    /** Whether prediction runs on personal averages yet (vs. defaults). */
    val isPersonalized: Boolean get() = periodStarts.size >= CyclePredictor.MIN_STARTS_FOR_AVERAGE

    /** Days logged for this profile, newest first. */
    fun loggedDays(): List<DayRecord> = dayRecords.values.sortedByDescending { it.date }

    /**
     * 증상 인사이트: 기록된 증상별 빈도(기록일 대비 %)와 나타난 주기 수. 자주/반복/예측 표시에 쓴다.
     * 로컬 계산만 하며 인터넷/서버가 필요 없다.
     */
    fun symptomStats(): List<SymptomStat> {
        val records = dayRecords.values
        if (records.isEmpty()) return emptyList()
        val starts = periodStarts.distinct().sorted()
        fun cycleIndexOf(d: LocalDate): Int = starts.count { !it.isAfter(d) } // 몇 번째 주기 구간인지(0=첫 기록 이전)
        val counts = HashMap<String, Int>()
        val cyclesBy = HashMap<String, MutableSet<Int>>()
        records.forEach { r ->
            val syms = r.symptoms?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()
            val ci = cycleIndexOf(r.date)
            syms.forEach { s ->
                counts[s] = (counts[s] ?: 0) + 1
                cyclesBy.getOrPut(s) { mutableSetOf() }.add(ci)
            }
        }
        val denom = records.size.coerceAtLeast(1)
        return counts.entries.map { (s, c) ->
            SymptomStat(name = s, count = c, percent = (c * 100 / denom), cyclesSeen = cyclesBy[s]?.size ?: 0)
        }.sortedByDescending { it.count }
    }

    /** 기록된 서로 다른 주기 구간 수(반복/예측 판단용). */
    fun recordedCycleCount(): Int = periodStarts.distinct().size
}

/** One row of the cycle-history table: a recorded period and the cycle length that followed it. */
data class CycleHistoryItem(val start: LocalDate, val end: LocalDate, val cycleLength: Int?)

/** 증상 통계 한 줄: 증상명, 기록 횟수, 기록일 대비 %, 나타난 주기 수. */
data class SymptomStat(val name: String, val count: Int, val percent: Int, val cyclesSeen: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = OloDatabase.get(app)
    private val selectedId = MutableStateFlow<Long?>(null)

    // 앱 잠금은 프로필별이 아니라 앱 전체 한 곳에서만 켠다(공통 설정). SharedPreferences 로 저장한다.
    private val prefs = app.getSharedPreferences("olo_settings", Application.MODE_PRIVATE)
    private val _appLockEnabled = MutableStateFlow(prefs.getBoolean(KEY_APP_LOCK, false))
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled

    fun setAppLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK, enabled).apply()
        _appLockEnabled.value = enabled
    }

    init {
        viewModelScope.launch { seedIfEmpty() }
    }

    private val profiles = db.profileDao().observeAll()

    val state: StateFlow<HomeState> =
        combine(profiles, selectedId) { list, sel -> list to sel }
            .flatMapLatest { (list, sel) ->
                val selected = list.firstOrNull { it.id == sel } ?: list.firstOrNull()
                if (selected == null) {
                    flowOf(HomeState(profiles = list))
                } else {
                    combine(
                        db.periodStartDao().observeForProfile(selected.id),
                        db.dayRecordDao().observeForProfile(selected.id),
                    ) { starts, records ->
                        buildState(list, selected, starts, records)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private fun buildState(
        list: List<Profile>,
        selected: Profile,
        starts: List<PeriodStart>,
        records: List<DayRecord>,
    ): HomeState {
        val today = LocalDate.now()
        val startDates = starts.map { it.startDate }
        val params = CyclePredictor.deriveParams(
            startDates,
            defaults = CycleParams(
                cycleLength = selected.defaultCycleLength,
                periodLength = selected.defaultPeriodLength,
            ),
        )
        val prediction = CyclePredictor.currentCycle(startDates, params, today)
        val cycleDayIndex = prediction?.let { it.periodStart.until(today).days + 1 }
        val gaps = startDates.sorted().zipWithNext { a, b -> a.until(b).days }
            .filter { it in CycleParams.MIN_CYCLE..CycleParams.MAX_CYCLE }
        return HomeState(
            profiles = list,
            selected = selected,
            periodStarts = startDates,
            dayRecords = records.associateBy { it.date },
            prediction = prediction,
            daysUntilNextPeriod = prediction?.let { CyclePredictor.daysUntilNextPeriod(it, today) },
            params = params,
            cycleDayIndex = cycleDayIndex,
            recentCycleLengths = gaps.takeLast(CyclePredictor.AVERAGE_WINDOW),
            today = today,
        )
    }

    fun select(profileId: Long) { selectedId.value = profileId }


    /** Sets or clears the period-start flag on [date] to match [isStart]. */
    fun setPeriodStart(date: LocalDate, isStart: Boolean) {
        val profile = state.value.selected ?: return
        val already = date in state.value.periodStarts
        if (isStart == already) return
        viewModelScope.launch {
            if (isStart) db.periodStartDao().insert(PeriodStart(profileId = profile.id, startDate = date))
            else db.periodStartDao().deleteByDate(profile.id, date)
        }
    }

    /** Upserts the day log for [date] (preserving the existing row id if any). */
    fun saveDayRecord(
        date: LocalDate,
        flow: Int?,
        symptoms: List<String>,
        mood: String?,
        temperature: Double?,
        memo: String?,
    ) {
        val profile = state.value.selected ?: return
        val existing = state.value.dayRecords[date]
        viewModelScope.launch {
            db.dayRecordDao().upsert(
                DayRecord(
                    id = existing?.id ?: 0,
                    profileId = profile.id,
                    date = date,
                    flow = flow,
                    symptoms = symptoms.takeIf { it.isNotEmpty() }?.joinToString(","),
                    mood = mood?.takeIf { it.isNotBlank() },
                    temperature = temperature,
                    weight = existing?.weight,
                    medication = existing?.medication,
                    memo = memo?.takeIf { it.isNotBlank() },
                ),
            )
        }
    }

    fun addProfile(
        name: String, color: Int, cycleLength: Int, periodLength: Int,
        onBirthControl: Boolean, photoPath: String?,
    ) {
        viewModelScope.launch {
            val order = state.value.profiles.size
            val id = db.profileDao().insert(
                Profile(
                    name = name.ifBlank { "구성원 ${order + 1}" },
                    color = color,
                    defaultCycleLength = cycleLength,
                    defaultPeriodLength = periodLength,
                    onBirthControl = onBirthControl,
                    photoPath = photoPath,
                    sortOrder = order,
                ),
            )
            selectedId.value = id
        }
    }

    /** Saves edits to an existing profile, keeping its id and sort order. */
    fun updateProfile(
        original: Profile, name: String, color: Int, cycleLength: Int, periodLength: Int,
        onBirthControl: Boolean, photoPath: String?,
    ) {
        viewModelScope.launch {
            // 사진을 새로 골랐거나 지웠으면, 더 이상 안 쓰는 예전 파일을 정리한다.
            if (original.photoPath != null && original.photoPath != photoPath) {
                ProfilePhotos.delete(original.photoPath)
            }
            db.profileDao().upsert(
                original.copy(
                    name = name.ifBlank { original.name },
                    color = color,
                    defaultCycleLength = cycleLength,
                    defaultPeriodLength = periodLength,
                    onBirthControl = onBirthControl,
                    photoPath = photoPath,
                ),
            )
        }
    }

    /** Deletes a profile and (via foreign keys) all of its records. */
    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            db.profileDao().delete(profile)
            ProfilePhotos.delete(profile.photoPath) // 사진 파일도 함께 정리한다.
            if (selectedId.value == profile.id) {
                selectedId.value = state.value.profiles.firstOrNull { it.id != profile.id }?.id
            }
        }
    }

    // ---- 암호화 백업 내보내기 / 복원 ------------------------------------------

    /** 전체 데이터(+사진)를 사용자 암호로 봉인한 백업 바이트를 만든다. IO 스레드에서 실행. */
    suspend fun exportEncrypted(passphrase: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val profiles = db.profileDao().getAll()
        val starts = db.periodStartDao().getAll()
        val records = db.dayRecordDao().getAll()
        val photos = buildMap<String, ByteArray> {
            profiles.forEach { p ->
                p.photoPath?.let { path ->
                    val f = File(path)
                    if (f.exists()) runCatching { put(f.name, f.readBytes()) }
                }
            }
        }
        val json = BackupCodec.encodeJson(BackupCodec.Bundle(profiles, starts, records, photos))
        BackupCodec.encrypt(json, passphrase)
    }

    /** 복원 방식: 현재 데이터를 전부 대체 / 현재 데이터에 합치기. */
    enum class ImportMode { REPLACE, MERGE }

    /**
     * 백업 바이트를 복호화해 복원한다. 암호가 틀리면 예외를 던져 UI가 안내한다(사진은 내부 저장소로 복구).
     * - REPLACE: 현재 데이터를 전부 지우고 백업 내용으로 대체(원본 id 유지).
     * - MERGE: 이름이 같은 구성원은 그 구성원에 기록을 합치고(같은 날짜는 기존 유지), 없는 구성원은 새로 추가.
     * 반환값은 복원(대체/합침)에 관여한 구성원 수.
     */
    suspend fun importEncrypted(blob: ByteArray, passphrase: CharArray, mode: ImportMode): Int = withContext(Dispatchers.IO) {
        val json = BackupCodec.decrypt(blob, passphrase)       // 암호 오류 시 AEADBadTagException
        val bundle = BackupCodec.decodeJson(json)
        val ctx = getApplication<Application>()

        if (mode == ImportMode.REPLACE) {
            // 기존 사진 파일 정리 후 DB 비우기(자식은 CASCADE). 그다음 원본 id 그대로 삽입.
            db.profileDao().getAll().forEach { ProfilePhotos.delete(it.photoPath) }
            db.profileDao().clearAll()
            bundle.profiles.forEach { p ->
                val newPath = p.photoPath?.let { name -> bundle.photos[name]?.let { ProfilePhotos.writeInternal(ctx, it) } }
                db.profileDao().insertKeepingId(p.copy(photoPath = newPath))
            }
            bundle.periodStarts.forEach { db.periodStartDao().insertKeepingId(it) }
            bundle.dayRecords.forEach { db.dayRecordDao().insertKeepingId(it) }
            selectedId.value = bundle.profiles.minByOrNull { it.sortOrder }?.id
            return@withContext bundle.profiles.size
        }

        // MERGE: 백업의 구 profileId → 현재 DB의 실제 id 로 매핑. 이름이 같으면 기존 구성원에 합치고, 없으면 추가.
        val existing = db.profileDao().getAll()
        val byName = existing.associateBy { it.name }
        var order = existing.size
        val idMap = HashMap<Long, Long>()
        bundle.profiles.forEach { p ->
            val target = byName[p.name]
            val targetId = if (target != null) {
                target.id // 기존 구성원에 합침(프로필 설정·사진은 기존 것을 유지).
            } else {
                val newPath = p.photoPath?.let { name -> bundle.photos[name]?.let { ProfilePhotos.writeInternal(ctx, it) } }
                db.profileDao().insert(p.copy(id = 0, photoPath = newPath, sortOrder = order++))
            }
            idMap[p.id] = targetId
        }
        // 자식 레코드는 새 id로 삽입하되 중복(같은 구성원·날짜/시작일)은 건너뛴다.
        bundle.periodStarts.forEach { s ->
            idMap[s.profileId]?.let { pid -> db.periodStartDao().insert(s.copy(id = 0, profileId = pid)) }
        }
        bundle.dayRecords.forEach { r ->
            idMap[r.profileId]?.let { pid -> db.dayRecordDao().insertIfAbsent(r.copy(id = 0, profileId = pid)) }
        }
        if (selectedId.value == null) selectedId.value = existing.firstOrNull()?.id ?: idMap.values.firstOrNull()
        bundle.profiles.size
    }

    private suspend fun seedIfEmpty() {
        if (db.profileDao().count() > 0) return
        db.profileDao().insert(
            Profile(name = "나", color = OloColors.ProfilePalette[0].toArgb(), sortOrder = 0),
        )
    }

    private companion object {
        const val KEY_APP_LOCK = "app_lock"
    }
}


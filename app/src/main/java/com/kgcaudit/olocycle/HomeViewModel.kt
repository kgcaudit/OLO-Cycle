package com.kgcaudit.olocycle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kgcaudit.olocycle.cycle.CycleParams
import com.kgcaudit.olocycle.cycle.CyclePrediction
import com.kgcaudit.olocycle.cycle.CyclePredictor
import com.kgcaudit.olocycle.cycle.Phase
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
import kotlinx.coroutines.launch
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
}

/** One row of the cycle-history table: a recorded period and the cycle length that followed it. */
data class CycleHistoryItem(val start: LocalDate, val end: LocalDate, val cycleLength: Int?)

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


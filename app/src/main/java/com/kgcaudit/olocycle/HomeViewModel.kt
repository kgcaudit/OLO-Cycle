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
    val today: LocalDate = LocalDate.now(),
) {
    /** Phase for [day], derived on demand so the calendar can color each cell. */
    fun phaseOf(day: LocalDate): Phase =
        prediction?.let { CyclePredictor.phaseOf(day, it) } ?: Phase.UNKNOWN

    fun isPeriodStart(day: LocalDate): Boolean = day in periodStarts

    fun recordOf(day: LocalDate): DayRecord? = dayRecords[day]

    /** True when the day carries any log entry the calendar should mark with a dot. */
    fun hasNote(day: LocalDate): Boolean = dayRecords[day]?.let {
        it.flow != null || !it.symptoms.isNullOrBlank() || !it.mood.isNullOrBlank() ||
            it.temperature != null || !it.memo.isNullOrBlank()
    } ?: false
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = OloDatabase.get(app)
    private val selectedId = MutableStateFlow<Long?>(null)

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
        return HomeState(
            profiles = list,
            selected = selected,
            periodStarts = startDates,
            dayRecords = records.associateBy { it.date },
            prediction = prediction,
            daysUntilNextPeriod = prediction?.let { CyclePredictor.daysUntilNextPeriod(it, today) },
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

    fun addProfile(name: String, color: Int, cycleLength: Int, periodLength: Int, locked: Boolean) {
        viewModelScope.launch {
            val order = state.value.profiles.size
            val id = db.profileDao().insert(
                Profile(
                    name = name.ifBlank { "구성원 ${order + 1}" },
                    color = color,
                    defaultCycleLength = cycleLength,
                    defaultPeriodLength = periodLength,
                    locked = locked,
                    sortOrder = order,
                ),
            )
            selectedId.value = id
        }
    }

    private suspend fun seedIfEmpty() {
        if (db.profileDao().count() > 0) return
        db.profileDao().insert(
            Profile(name = "나", color = OloColors.ProfilePalette[0].toArgb(), sortOrder = 0),
        )
    }
}


package com.kgcaudit.olocycle.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** A household member. Each member's cycle data is isolated by [id]. */
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB accent color for this member. */
    val color: Int,
    val defaultCycleLength: Int = 28,
    val defaultPeriodLength: Int = 5,
    val onBirthControl: Boolean = false,
    /** Requires biometric/PIN before this profile's data is shown. */
    val locked: Boolean = false,
    /** Absolute path to a photo copied into app-internal storage, or null for the colour+initial avatar. */
    val photoPath: String? = null,
    val sortOrder: Int = 0,
)

/** A recorded period-start (with optional end), the backbone of prediction. */
@Entity(
    tableName = "period_starts",
    foreignKeys = [ForeignKey(
        entity = Profile::class, parentColumns = ["id"], childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("profileId"), Index(value = ["profileId", "startDate"], unique = true)],
)
data class PeriodStart(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
)

/** An optional per-day log entry (flow, symptoms, mood, etc.), scoped to one profile. */
@Entity(
    tableName = "day_records",
    foreignKeys = [ForeignKey(
        entity = Profile::class, parentColumns = ["id"], childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("profileId"), Index(value = ["profileId", "date"], unique = true)],
)
data class DayRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val date: LocalDate,
    val flow: Int? = null,          // 0..3 (없음/적음/보통/많음)
    val symptoms: String? = null,   // comma-separated tags
    val mood: String? = null,
    val temperature: Double? = null,
    val weight: Double? = null,
    val medication: String? = null,
    val memo: String? = null,
)

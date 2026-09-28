package com.kgcaudit.olocycle.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Profile>>

    @Insert
    suspend fun insert(profile: Profile): Long

    @Upsert
    suspend fun upsert(profile: Profile)

    @Delete
    suspend fun delete(profile: Profile)

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int
}

@Dao
interface PeriodStartDao {
    @Query("SELECT * FROM period_starts WHERE profileId = :profileId ORDER BY startDate")
    fun observeForProfile(profileId: Long): Flow<List<PeriodStart>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(start: PeriodStart): Long

    @Query("DELETE FROM period_starts WHERE profileId = :profileId AND startDate = :startDate")
    suspend fun deleteByDate(profileId: Long, startDate: java.time.LocalDate)
}

@Dao
interface DayRecordDao {
    @Query("SELECT * FROM day_records WHERE profileId = :profileId")
    fun observeForProfile(profileId: Long): Flow<List<DayRecord>>

    @Upsert
    suspend fun upsert(record: DayRecord)
}

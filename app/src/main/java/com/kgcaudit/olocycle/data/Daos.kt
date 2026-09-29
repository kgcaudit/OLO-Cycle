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

    // 백업용: 전체 조회 · 전체 삭제(자식 테이블은 FK CASCADE로 함께 지워진다) · id 유지 삽입.
    @Query("SELECT * FROM profiles")
    suspend fun getAll(): List<Profile>

    @Query("DELETE FROM profiles")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeepingId(profile: Profile)
}

@Dao
interface PeriodStartDao {
    @Query("SELECT * FROM period_starts WHERE profileId = :profileId ORDER BY startDate")
    fun observeForProfile(profileId: Long): Flow<List<PeriodStart>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(start: PeriodStart): Long

    @Query("DELETE FROM period_starts WHERE profileId = :profileId AND startDate = :startDate")
    suspend fun deleteByDate(profileId: Long, startDate: java.time.LocalDate)

    @Query("SELECT * FROM period_starts")
    suspend fun getAll(): List<PeriodStart>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeepingId(start: PeriodStart)
}

@Dao
interface DayRecordDao {
    @Query("SELECT * FROM day_records WHERE profileId = :profileId")
    fun observeForProfile(profileId: Long): Flow<List<DayRecord>>

    @Upsert
    suspend fun upsert(record: DayRecord)

    @Query("SELECT * FROM day_records")
    suspend fun getAll(): List<DayRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeepingId(record: DayRecord)

    /** 합치기(merge)용: 같은 (구성원, 날짜)가 이미 있으면 기존 기록을 두고 건너뛴다. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(record: DayRecord): Long
}

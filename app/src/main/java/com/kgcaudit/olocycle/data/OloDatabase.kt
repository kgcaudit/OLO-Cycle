package com.kgcaudit.olocycle.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Profile::class, PeriodStart::class, DayRecord::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class OloDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun periodStartDao(): PeriodStartDao
    abstract fun dayRecordDao(): DayRecordDao

    companion object {
        @Volatile private var instance: OloDatabase? = null

        fun get(context: Context): OloDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, OloDatabase::class.java, "olo-cycle.db",
            ).build().also { instance = it }
        }
    }
}

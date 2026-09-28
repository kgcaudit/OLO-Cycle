package com.kgcaudit.olocycle.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Profile::class, PeriodStart::class, DayRecord::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class OloDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun periodStartDao(): PeriodStartDao
    abstract fun dayRecordDao(): DayRecordDao

    companion object {
        @Volatile private var instance: OloDatabase? = null

        /** v2: adds Profile.photoPath. Additive column so existing records/profiles are preserved. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN photoPath TEXT")
            }
        }

        fun get(context: Context): OloDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, OloDatabase::class.java, "olo-cycle.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}

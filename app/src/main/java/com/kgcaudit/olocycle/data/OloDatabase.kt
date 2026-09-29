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
    version = 3,
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

        /**
         * v3: 색 체계 개편. 구성원 팔레트의 틸(#3E7F80)이 새 가임기색(#5F8F86)과 충돌하므로,
         * 그 색으로 저장된 기존 구성원을 더스티 블루(#5B6E86)로 옮긴다. 데이터는 그대로 보존.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val oldTeal = 0xFF3E7F80.toInt()
                val newBlue = 0xFF5B6E86.toInt()
                db.execSQL("UPDATE profiles SET color = ? WHERE color = ?", arrayOf<Any>(newBlue, oldTeal))
            }
        }

        fun get(context: Context): OloDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, OloDatabase::class.java, "olo-cycle.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}

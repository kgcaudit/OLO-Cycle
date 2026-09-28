package com.kgcaudit.olocycle.data

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter fun dateToEpoch(date: LocalDate?): Long? = date?.toEpochDay()
    @TypeConverter fun epochToDate(epoch: Long?): LocalDate? = epoch?.let(LocalDate::ofEpochDay)
}

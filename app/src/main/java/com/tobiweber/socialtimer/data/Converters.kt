package com.tobiweber.socialtimer.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromAppState(value: AppState): String = value.name

    @TypeConverter
    fun toAppState(value: String): AppState = AppState.valueOf(value)
}

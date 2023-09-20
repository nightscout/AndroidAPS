package com.microtechmd.equil.data

import androidx.room.TypeConverter
import com.google.gson.GsonBuilder

data class SettingProfile(

    // var timestamp: Long = 0
    // var injectStart: Long = 0
    // var injectStop: Long = 0
    // var stop: Boolean = false
    // var insulin: Double = 0.0
    var alarmMode: AlarmMode = AlarmMode.TONE_AND_SHAKE,
    var lowAlarm: Double = 10.0,
    var closeTime: Long = 0,
    // var runMode: RunMode = RunMode.NONE,
    var useTime: Long = 0,
    var largeFastAlarm: Double = 0.0,
    var stopAlarm: Double = 0.0,
    var infusionUnit: Double = 0.0,
    var basalAlarm: Double = 0.0,
    var largeAlarm: Double = 0.0,

    // val largefast: Double = 0.0,
    // val stop: Double = 0.0,
    // val infusionUnit: Double = 0.0,
    // val basal: Double = 0.0,
    // val large: Double = 0.0
) {
    // val largefastAlarm: Double = 0.0
    // val stopAlarm: Double = 0.0
    // val infusionUnit: Double = 0.0
    // val basalAlarm: Double = 0.0
    // val largeAlarm: Double = 0.0

    @TypeConverter
    fun toJson(): String {
        val gson = GsonBuilder().create()
        return gson.toJson(this)
    }
}
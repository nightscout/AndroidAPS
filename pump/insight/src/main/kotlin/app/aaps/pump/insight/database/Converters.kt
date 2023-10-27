package app.aaps.pump.insight.database

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromEventType(evenType: InsightPumpID.EventType) = evenType.name

    @TypeConverter
    fun toEventType(evenType: String?) = evenType?.let { InsightPumpID.EventType.valueOf(it) }
}
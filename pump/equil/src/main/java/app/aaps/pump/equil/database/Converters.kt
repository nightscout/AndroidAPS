package app.aaps.pump.equil.database

import androidx.room.TypeConverter
import app.aaps.core.interfaces.profile.Profile
import com.google.gson.GsonBuilder

class Converters {

    @TypeConverter
    fun toBolusType(s: String) = enumValueOf<BolusType>(s)

    @TypeConverter
    fun fromBolusType(bolusType: BolusType) = bolusType.name

    @TypeConverter
    fun toSegments(s: String?): List<Profile.ProfileValue> {
        s ?: return emptyList()
        val gson = GsonBuilder().create()
        return gson.fromJson(s, Array<Profile.ProfileValue>::class.java).toList()
    }

    @TypeConverter
    fun fromBasalValues(segments: List<Profile.ProfileValue>): String {
        val gson = GsonBuilder().create()
        return gson.toJson(segments)
    }
}

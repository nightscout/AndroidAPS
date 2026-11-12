package app.aaps.pump.omnipod.omnipod5.history.database

import androidx.room.TypeConverter
import app.aaps.core.interfaces.profile.Profile
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BolusType
import app.aaps.pump.omnipod.common.history.data.InitialResult
import app.aaps.pump.omnipod.common.history.data.ResolvedResult
import com.google.gson.GsonBuilder

class Converters {

    @TypeConverter fun toInitialResult(value: String) = enumValueOf<InitialResult>(value)
    @TypeConverter fun fromInitialResult(value: InitialResult) = value.name
    @TypeConverter fun toResolvedResult(value: String?) = value?.let { enumValueOf<ResolvedResult>(it) }
    @TypeConverter fun fromResolvedResult(value: ResolvedResult?) = value?.name
    @TypeConverter
    fun toBolusType(value: String?): BolusType? = when (value) {
        "BASAL_DRIFT_COMPENSATION" -> BolusType.BASAL_CORRECTION
        else -> value?.let { enumValueOf<BolusType>(it) }
    }

    // Keep the stored name used by older Omnipod 5 builds.
    @TypeConverter
    fun fromBolusType(value: BolusType?): String? = when (value) {
        BolusType.BASAL_CORRECTION -> "BASAL_DRIFT_COMPENSATION"
        else -> value?.name
    }

    @TypeConverter fun toCommandType(value: String) = enumValueOf<OmnipodCommandType>(value)
    @TypeConverter fun fromCommandType(value: OmnipodCommandType) = value.name

    @TypeConverter
    fun toSegments(value: String?): List<Profile.ProfileValue> =
        value?.let { GsonBuilder().create().fromJson(it, Array<Profile.ProfileValue>::class.java).toList() } ?: emptyList()

    @TypeConverter
    fun fromSegments(value: List<Profile.ProfileValue>): String = GsonBuilder().create().toJson(value)
}

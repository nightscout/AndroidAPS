package info.nightscout.androidaps.plugins.pump.omnipod.dash.history.database

import androidx.room.TypeConverter
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.BolusType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.InitialResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.ResolvedResult

class Converters {

    @TypeConverter
    fun toBolusType(s: String) = enumValueOf<BolusType>(s)

    @TypeConverter
    fun fromBolusType(bolusType: BolusType) = bolusType.name

    @TypeConverter
    fun toInitialResult(s: String) = enumValueOf<InitialResult>(s)

    @TypeConverter
    fun fromInitialResult(initialResult: InitialResult) = initialResult.name

    @TypeConverter
    fun toResolvedResult(s: String?) = s?.let { enumValueOf<ResolvedResult>(it) }

    @TypeConverter
    fun fromResolvedResult(resolvedResult: ResolvedResult?) = resolvedResult?.name

    @TypeConverter
    fun toOmnipodCommandType(s: String) = enumValueOf<OmnipodCommandType>(s)

    @TypeConverter
    fun fromOmnipodCommandType(omnipodCommandType: OmnipodCommandType) = omnipodCommandType.name
}

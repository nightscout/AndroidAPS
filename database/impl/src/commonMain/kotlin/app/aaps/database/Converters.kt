package app.aaps.database

import androidx.room.TypeConverter
import app.aaps.database.entities.APSResult
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.UserEntry.Action
import app.aaps.database.entities.UserEntry.Sources
import app.aaps.database.entities.ValueWithUnit
import app.aaps.database.entities.data.Block
import app.aaps.database.entities.data.GlucoseUnit
import app.aaps.database.entities.data.TargetBlock
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.serialisation.ValueWithUnitSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json

    @TypeConverter
    fun fromAction(action: Action?) = action?.name

    @TypeConverter
    fun toAction(action: String?) = action?.let { Action.valueOf(it) }

    @TypeConverter
    fun fromSource(source: Sources?) = source?.name

    @TypeConverter
    fun toSource(source: String?) = source?.let { Sources.fromString(it) }

    @TypeConverter
    fun fromListOfValueWithUnit(values: List<ValueWithUnit>): String =
        json.encodeToString(values.map(::ValueWithUnitWrapper))

    @TypeConverter
    fun toMutableListOfValueWithUnit(string: String): List<ValueWithUnit> =
        json.decodeFromString<List<ValueWithUnitWrapper>>(string).map { it.wrapped }

    /**
     * The `wrapped` layer is part of the stored format - Gson wrote this class, not the sealed class
     * itself - so it stays, and its property name is as load bearing as the subtype names are.
     */
    @Serializable
    private class ValueWithUnitWrapper(@Serializable(with = ValueWithUnitSerializer::class) val wrapped: ValueWithUnit)

    @TypeConverter
    fun fromBolusType(bolusType: Bolus.Type?) = bolusType?.name

    @TypeConverter
    fun toBolusType(bolusType: String?) = bolusType?.let { Bolus.Type.valueOf(it) }

    @TypeConverter
    fun fromTrendArrow(trendArrow: GlucoseValue.TrendArrow?) = trendArrow?.name

    @TypeConverter
    fun toTrendArrow(trendArrow: String?) = trendArrow?.let { GlucoseValue.TrendArrow.valueOf(it) }

    @TypeConverter
    fun fromSourceSensor(sourceSensor: GlucoseValue.SourceSensor?) = sourceSensor?.name

    @TypeConverter
    fun toSourceSensor(sourceSensor: String?): GlucoseValue.SourceSensor? {
        return sourceSensor?.let {
            GlucoseValue.SourceSensor.entries.firstOrNull { enumValue -> enumValue.name == it } ?: GlucoseValue.SourceSensor.UNKNOWN
        }
    }

    @TypeConverter
    fun fromTBRType(tbrType: TemporaryBasal.Type?) = tbrType?.name

    @TypeConverter
    fun toTBRType(tbrType: String?) = tbrType?.let { TemporaryBasal.Type.valueOf(it) }

    @TypeConverter
    fun fromTempTargetReason(tempTargetReason: TemporaryTarget.Reason?) = tempTargetReason?.name

    @TypeConverter
    fun toTempTargetReason(tempTargetReason: String?) = tempTargetReason?.let { TemporaryTarget.Reason.valueOf(it) }

    @TypeConverter
    fun fromTherapyEventType(therapyEventType: TherapyEvent.Type?) = therapyEventType?.name

    @TypeConverter
    fun toTherapyEventType(therapyEventType: String?) = therapyEventType?.let { TherapyEvent.Type.valueOf(it) }

    @TypeConverter
    fun fromTherapyEventLocation(therapyEventLocation: TherapyEvent.Location?) = therapyEventLocation?.name

    @TypeConverter
    fun toTherapyEventLocation(therapyEventLocation: String?): TherapyEvent.Location? = therapyEventLocation?.let { TherapyEvent.Location.valueOf(it) }

    @TypeConverter
    fun fromTherapyEventArrow(therapyEventArrow: TherapyEvent.Arrow?) = therapyEventArrow?.name

    @TypeConverter
    fun toTherapyEventArrow(therapyEventArrow: String?): TherapyEvent.Arrow? = therapyEventArrow?.let { TherapyEvent.Arrow.valueOf(it) }

    @TypeConverter
    fun fromGlucoseType(meterType: TherapyEvent.MeterType?) = meterType?.name

    @TypeConverter
    fun toGlucoseType(meterType: String?) = meterType?.let { TherapyEvent.MeterType.valueOf(it) }

    @TypeConverter
    fun fromGlucoseUnit(glucoseUnit: GlucoseUnit?) = glucoseUnit?.name

    @TypeConverter
    fun toGlucoseUnit(glucoseUnit: String?) = glucoseUnit?.let { GlucoseUnit.valueOf(it) }

    @TypeConverter
    fun fromPumpType(pumpType: InterfaceIDs.PumpType?) = pumpType?.name

    @TypeConverter
    fun toPumpType(pumpType: String?) = pumpType?.let { InterfaceIDs.PumpType.fromString(it) }

    @TypeConverter
    fun fromAlgorithm(algorithm: APSResult.Algorithm?) = algorithm?.name

    @TypeConverter
    fun toAlgorithm(algorithm: String?) = algorithm?.let { APSResult.Algorithm.valueOf(it) }

    // The profile blocks used to go through org.json, which exists only on Android. kotlinx writes the
    // same keys and reads either form; it differs only in printing a whole double as "1.0" where
    // org.json printed "1", and in keeping the declared key order where org.json sorted it. Rows stay
    // readable in both directions, which is what ProfileBlocksFormatTest pins - not the exact bytes.
    @TypeConverter
    fun fromListOfBlocks(blocks: List<Block>?): String? = blocks?.let(json::encodeToString)

    @TypeConverter
    fun toListOfBlocks(jsonString: String?): List<Block>? = jsonString?.let(json::decodeFromString)

    @TypeConverter
    fun fromListOfTargetBlocks(blocks: List<TargetBlock>?): String? = blocks?.let(json::encodeToString)

    @TypeConverter
    fun toListOfTargetBlocks(jsonString: String?): List<TargetBlock>? = jsonString?.let(json::decodeFromString)

    @TypeConverter
    fun fromRunningModeMode(mode: RunningMode.Mode?) = mode?.name

    @TypeConverter
    fun toRunningModeMode(mode: String?) = mode?.let { RunningMode.Mode.valueOf(it) }
}

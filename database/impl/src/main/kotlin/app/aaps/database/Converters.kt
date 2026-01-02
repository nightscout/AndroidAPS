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
import app.aaps.database.serialisation.SealedClassHelper
import app.aaps.database.serialisation.fromJson
import org.json.JSONArray
import org.json.JSONObject

class Converters {

    @TypeConverter
    fun fromAction(action: Action?) = action?.name

    @TypeConverter
    fun toAction(action: String?) = action?.let { Action.valueOf(it) }

    @TypeConverter
    fun fromSource(source: Sources?) = source?.name

    @TypeConverter
    fun toSource(source: String?) = source?.let { Sources.fromString(it) }

    @TypeConverter
    fun fromListOfValueWithUnit(values: List<ValueWithUnit>): String = values.map(Converters::ValueWithUnitWrapper)
        .let(SealedClassHelper.gson::toJson)

    @TypeConverter
    fun toMutableListOfValueWithUnit(string: String): List<ValueWithUnit> = SealedClassHelper.gson
        .fromJson<List<ValueWithUnitWrapper>>(string).map { it.wrapped }

    private class ValueWithUnitWrapper(val wrapped: ValueWithUnit)

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
    fun toPumpType(pumpType: String?) = pumpType?.let { InterfaceIDs.PumpType.valueOf(it) }

    @TypeConverter
    fun fromAlgorithm(algorithm: APSResult.Algorithm?) = algorithm?.name

    @TypeConverter
    fun toAlgorithm(algorithm: String?) = algorithm?.let { APSResult.Algorithm.valueOf(it) }

    @TypeConverter
    fun fromListOfBlocks(blocks: List<Block>?): String? {
        if (blocks == null) return null
        val jsonArray = JSONArray()
        blocks.forEach {
            val jsonObject = JSONObject()
            jsonObject.put("duration", it.duration)
            jsonObject.put("amount", it.amount)
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toListOfBlocks(jsonString: String?): List<Block>? {
        if (jsonString == null) return null
        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<Block>()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            list.add(Block(jsonObject.getLong("duration"), jsonObject.getDouble("amount")))
        }
        return list
    }

    @TypeConverter
    fun fromListOfTargetBlocks(blocks: List<TargetBlock>?): String? {
        if (blocks == null) return null
        val jsonArray = JSONArray()
        blocks.forEach {
            val jsonObject = JSONObject()
            jsonObject.put("duration", it.duration)
            jsonObject.put("lowTarget", it.lowTarget)
            jsonObject.put("highTarget", it.highTarget)
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toListOfTargetBlocks(jsonString: String?): List<TargetBlock>? {
        if (jsonString == null) return null
        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<TargetBlock>()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            list.add(
                TargetBlock(
                    jsonObject.getLong("duration"),
                    jsonObject.getDouble("lowTarget"),
                    jsonObject.getDouble("highTarget")
                )
            )
        }
        return list
    }

    @TypeConverter
    fun fromRunningModeMode(mode: RunningMode.Mode?) = mode?.name

    @TypeConverter
    fun toRunningModeMode(mode: String?) = mode?.let { RunningMode.Mode.valueOf(it) }
}

package info.nightscout.androidaps.database

import androidx.room.TypeConverter
import info.nightscout.androidaps.database.data.Block
import info.nightscout.androidaps.database.data.TargetBlock
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.*
import org.json.JSONArray
import org.json.JSONObject

class Converters {

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
    fun toSourceSensor(sourceSensor: String?) = sourceSensor?.let { GlucoseValue.SourceSensor.valueOf(it) }

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
    fun fromGlucoseUnit(glucoseUnit: ProfileSwitch.GlucoseUnit?) = glucoseUnit?.name

    @TypeConverter
    fun toGlucoseUnit(glucoseUnit: String?) = glucoseUnit?.let { ProfileSwitch.GlucoseUnit.valueOf(it) }

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
    fun anyToString(value: Any?) = when (value) {
        null -> null
        is String -> "S$value"
        is Int -> "I$value"
        is Long -> "L$value"
        is Boolean -> "B$value"
        is Float -> "F$value"
        else -> throw IllegalArgumentException("Type not supported")
    }

    @TypeConverter
    fun stringToAny(value: String?): Any? = when {
        value == null -> null
        value.startsWith("S") -> value.substring(1)
        value.startsWith("I") -> value.substring(1).toInt()
        value.startsWith("L") -> value.substring(1).toLong()
        value.startsWith("B") -> value.substring(1).toBoolean()
        value.startsWith("F") -> value.substring(1).toFloat()
        else -> throw IllegalArgumentException("Type not supported")
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
            list.add(TargetBlock(jsonObject.getLong("duration"),
                jsonObject.getDouble("lowTarget"),
                jsonObject.getDouble("highTarget")))
        }
        return list
    }

}
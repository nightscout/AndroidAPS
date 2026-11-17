package app.aaps.pump.carelevo.data.model.entities

import app.aaps.pump.carelevo.data.common.AlarmCauseByNameSerializer
import app.aaps.pump.carelevo.domain.type.AlarmCause
import kotlinx.serialization.Serializable

// NOTE: these entities are stored as JSON (data/common/CarelevoJson.kt). Timestamps are deliberately REQUIRED
// parameters: a `= Clock.System.now().toString()` default would be evaluated at call time, so a record would get a
// time nobody chose. The mappers in data/mapper always pass every field explicitly.

data class CarelevoInfusionInfoEntity(
    val basalInfusionInfo: CarelevoBasalInfusionInfoEntity? = null,
    val tempBasalInfusionInfo: CarelevoTempBasalInfusionInfoEntity? = null,
    val immeBolusInfusionInfo: CarelevoImmeBolusInfusionInfoEntity? = null,
    val extendBolusInfusionInfo: CarelevoExtendBolusInfusionInfoEntity? = null
)

@Serializable
data class CarelevoBasalSegmentInfusionInfoEntity(
    val createdAt: String,
    val updatedAt: String,
    val startTime: Int,
    val endTime: Int,
    val speed: Double
)

@Serializable
data class CarelevoBasalInfusionInfoEntity(
    val infusionId: String,
    val address: String,
    val mode: Int,
    val createdAt: String,
    val updatedAt: String,
    val segments: List<CarelevoBasalSegmentInfusionInfoEntity>,
    val isStop: Boolean
)

@Serializable
data class CarelevoTempBasalInfusionInfoEntity(
    val infusionId: String,
    val address: String,
    val mode: Int,
    val createdAt: String,
    val updatedAt: String,
    val percent: Int? = null,
    val speed: Double? = null,
    val infusionDurationMin: Int? = null
)

@Serializable
data class CarelevoImmeBolusInfusionInfoEntity(
    val infusionId: String,
    val address: String,
    val mode: Int,
    val createdAt: String,
    val updatedAt: String,
    val volume: Double? = null,
    val infusionDurationSeconds: Int? = null,
    val pumpId: Long? = null,
    val bolusTotalBefore: Double? = null,
    val bolusType: String? = null
)

@Serializable
data class CarelevoExtendBolusInfusionInfoEntity(
    val infusionId: String,
    val address: String,
    val mode: Int,
    val createdAt: String,
    val updatedAt: String,
    val volume: Double? = null,
    val speed: Double? = null,
    val infusionDurationMin: Int? = null
)

@Serializable
data class CarelevoAlarmInfoEntity(
    val alarmId: String,
    val alarmType: Int,
    @Serializable(with = AlarmCauseByNameSerializer::class)
    val cause: AlarmCause,
    val value: Int? = null,
    val createdAt: String,
    val updatedAt: String,
    val acknowledged: Boolean,
    // Records stored before this field existed read as seen once. Gson read them as 0.
    val occurrenceCount: Int = 1
)

package info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion

import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import org.joda.time.DateTime

data class CarelevoInfusionInfoDomainModel(
    val basalInfusionInfo : CarelevoBasalInfusionInfoDomainModel? = null,
    val tempBasalInfusionInfo : CarelevoTempBasalInfusionInfoDomainModel? = null,
    val immeBolusInfusionInfo : CarelevoImmeBolusInfusionInfoDomainModel? = null,
    val extendBolusInfusionInfo : CarelevoExtendBolusInfusionInfoDomainModel? = null
) : CarelevoUseCaseResponse

data class CarelevoBasalSegmentInfusionInfoDomainModel(
    val createdAt : DateTime = DateTime.now(),
    val updatedAt : DateTime = DateTime.now(),
    val startTime : Int,
    val endTime : Int,
    val speed : Double
)

data class CarelevoBasalInfusionInfoDomainModel(
    val infusionId : String,
    val address : String,
    val mode : Int,
    val createdAt : DateTime = DateTime.now(),
    val updatedAt: DateTime = DateTime.now(),
    val segments : List<CarelevoBasalSegmentInfusionInfoDomainModel>,
    val isStop : Boolean
)

data class CarelevoTempBasalInfusionInfoDomainModel(
    val infusionId : String,
    val address : String,
    val mode : Int,
    val createdAt : DateTime = DateTime.now(),
    val updatedAt : DateTime = DateTime.now(),
    val percent : Int? = null,
    val speed : Double? = null,
    val infusionDurationMin : Int? = null
)

data class CarelevoImmeBolusInfusionInfoDomainModel(
    val infusionId : String,
    val address : String,
    val mode : Int,
    val createdAt : DateTime = DateTime.now(),
    val updatedAt : DateTime = DateTime.now(),
    val volume : Double? = null,
    val infusionDurationSeconds : Int? = null
)

data class CarelevoExtendBolusInfusionInfoDomainModel(
    val infusionId : String,
    val address : String,
    val mode : Int,
    val createdAt : DateTime = DateTime.now(),
    val updatedAt : DateTime = DateTime.now(),
    val volume : Double? = null,
    val speed : Double? = null,
    val infusionDurationMin : Int? = null
)
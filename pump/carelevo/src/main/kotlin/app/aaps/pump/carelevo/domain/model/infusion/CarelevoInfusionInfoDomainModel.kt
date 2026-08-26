package app.aaps.pump.carelevo.domain.model.infusion

import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import org.joda.time.DateTime

data class CarelevoInfusionInfoDomainModel(
    val basalInfusionInfo: CarelevoBasalInfusionInfoDomainModel? = null,
    val tempBasalInfusionInfo: CarelevoTempBasalInfusionInfoDomainModel? = null,
    val immeBolusInfusionInfo: CarelevoImmeBolusInfusionInfoDomainModel? = null,
    val extendBolusInfusionInfo: CarelevoExtendBolusInfusionInfoDomainModel? = null
) : CarelevoUseCaseResponse

/**
 * The patch `mode` wire values persisted into `CarelevoPatchInfoDomainModel.mode` (vendor protocol;
 * 4 is unused by this driver).
 */
object CarelevoPatchMode {

    const val BASAL_STOPPED = 0
    const val BASAL_RUNNING = 1
    const val TEMP_BASAL = 2
    const val IMME_BOLUS = 3
    const val EXTEND_BOLUS = 5
}

/**
 * Recompute the patch `mode` from what is still running, highest-priority active infusion first
 * (extended bolus > immediate bolus > temp basal > basal running/stopped). Returns null when NO
 * infusion record exists at all — callers decide whether that is an error (`?: throw`, the
 * mid-therapy persists) or means the patch is stopped (`?: BASAL_STOPPED`, the delete/discard
 * path). Single shared mode derivation used by the cancel/finish use cases.
 */
fun CarelevoInfusionInfoDomainModel.derivePatchMode(): Int? = when {
    extendBolusInfusionInfo != null -> CarelevoPatchMode.EXTEND_BOLUS
    immeBolusInfusionInfo != null   -> CarelevoPatchMode.IMME_BOLUS
    tempBasalInfusionInfo != null   -> CarelevoPatchMode.TEMP_BASAL
    basalInfusionInfo != null       -> if (basalInfusionInfo.isStop) CarelevoPatchMode.BASAL_STOPPED else CarelevoPatchMode.BASAL_RUNNING
    else                            -> null
}

data class CarelevoBasalSegmentInfusionInfoDomainModel(
    val createdAt: DateTime = DateTime.now(),
    val updatedAt: DateTime = DateTime.now(),
    val startTime: Int,
    val endTime: Int,
    val speed: Double
)

data class CarelevoBasalInfusionInfoDomainModel(
    val infusionId: String,
    val address: String,
    val mode: Int,
    val createdAt: DateTime = DateTime.now(),
    val updatedAt: DateTime = DateTime.now(),
    val segments: List<CarelevoBasalSegmentInfusionInfoDomainModel>,
    val isStop: Boolean
)

data class CarelevoTempBasalInfusionInfoDomainModel(
    val infusionId: String,
    val address: String,
    val mode: Int,
    val createdAt: DateTime = DateTime.now(),
    val updatedAt: DateTime = DateTime.now(),
    val percent: Int? = null,
    val speed: Double? = null,
    val infusionDurationMin: Int? = null
)

data class CarelevoImmeBolusInfusionInfoDomainModel(
    val infusionId: String,
    val address: String,
    val mode: Int,
    val createdAt: DateTime = DateTime.now(),
    val updatedAt: DateTime = DateTime.now(),
    val volume: Double? = null,
    val infusionDurationSeconds: Int? = null
)

data class CarelevoExtendBolusInfusionInfoDomainModel(
    val infusionId: String,
    val address: String,
    val mode: Int,
    val createdAt: DateTime = DateTime.now(),
    val updatedAt: DateTime = DateTime.now(),
    val volume: Double? = null,
    val speed: Double? = null,
    val infusionDurationMin: Int? = null
)
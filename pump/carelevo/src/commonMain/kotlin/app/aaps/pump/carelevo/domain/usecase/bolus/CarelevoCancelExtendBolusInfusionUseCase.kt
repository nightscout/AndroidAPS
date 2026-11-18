package app.aaps.pump.carelevo.domain.usecase.bolus

import app.aaps.pump.carelevo.domain.model.infusion.derivePatchMode
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import dev.zacsweers.metro.Inject
import kotlin.time.Clock

class CarelevoCancelExtendBolusInfusionUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    /**
     * The pump id of the AAPS record of the running extended bolus, from the store itself (the infusion-info stream
     * follows a moment later). Null when none runs, or for one started by a build that did not keep it. Read it
     * before [persistExtendBolusCancelled], which deletes it.
     */
    fun runningExtendBolusPumpId(): Long? = infusionInfoRepository.getInfusionInfoBySync()?.extendBolusInfusionInfo?.pumpId

    /**
     * Persist a canceled extended bolus (delete the extend-bolus infusion-info + recompute the patch mode
     * from the remaining infusion state) after a successful cancel-extend-bolus write. Returns false with no
     * infusion/patch record or if any write fails. The amount the patch reports is written to the AAPS record by
     * the caller.
     */
    fun persistExtendBolusCancelled(): Boolean = runCatching {
        require(infusionInfoRepository.deleteExtendBolusInfusionInfo()) { "delete extend bolus infusion info is failed" }

        val infusionInfo = infusionInfoRepository.getInfusionInfoBySync()
            ?: throw NullPointerException("infusion info must be not null")

        val mode = infusionInfo.derivePatchMode()
            ?: throw NullPointerException("infusion info must be not null")

        val patchInfo = patchInfoRepository.getPatchInfoBySync()
            ?: throw NullPointerException("patch info must be not null")
        require(patchInfoRepository.updatePatchInfo(patchInfo.copy(updatedAt = Clock.System.now(), mode = mode))) { "update patch info is failed" }
    }.isSuccess
}

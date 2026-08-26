package app.aaps.pump.carelevo.domain.usecase.bolus

import app.aaps.pump.carelevo.domain.model.infusion.derivePatchMode
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoCancelExtendBolusInfusionUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    /**
     * Persist a canceled extended bolus (delete the extend-bolus infusion-info + recompute the patch mode
     * from the remaining infusion state) after a successful cancel-extend-bolus write. Returns false with no
     * infusion/patch record or if any write fails. (The pump-reported `infusedAmount` is surfaced by the
     * caller, not persisted here — it is not reconciled into the DB.)
     */
    fun persistExtendBolusCancelled(): Boolean = runCatching {
        require(infusionInfoRepository.deleteExtendBolusInfusionInfo()) { "delete extend bolus infusion info is failed" }

        val infusionInfo = infusionInfoRepository.getInfusionInfoBySync()
            ?: throw NullPointerException("infusion info must be not null")

        val mode = infusionInfo.derivePatchMode()
            ?: throw NullPointerException("infusion info must be not null")

        val patchInfo = patchInfoRepository.getPatchInfoBySync()
            ?: throw NullPointerException("patch info must be not null")
        require(patchInfoRepository.updatePatchInfo(patchInfo.copy(updatedAt = DateTime.now(), mode = mode))) { "update patch info is failed" }
    }.isSuccess
}

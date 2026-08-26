package app.aaps.pump.carelevo.domain.usecase.bolus

import app.aaps.pump.carelevo.domain.model.infusion.derivePatchMode
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoCancelImmeBolusInfusionUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    /**
     * Persist a cancelled immediate bolus (delete the imme-bolus infusion-info + recompute the patch mode from
     * the remaining infusion state) after a successful cancel-imme-bolus write. The pump-reported
     * `infusedAmount` is recorded to the AAPS treatment DB by the caller (not here). Returns false with no
     * infusion/patch record or if any write fails.
     */
    fun persistImmeBolusCancelled(): Boolean = runCatching {
        require(infusionInfoRepository.deleteImmeBolusInfusionInfo()) { "delete imme bolus infusion info is failed" }

        val infusionInfo = infusionInfoRepository.getInfusionInfoBySync()
            ?: throw NullPointerException("infusion info must be not null")

        val mode = infusionInfo.derivePatchMode()
            ?: throw NullPointerException("infusion info must be not null")

        val patchInfo = patchInfoRepository.getPatchInfoBySync()
            ?: throw NullPointerException("patch info must be not null")
        require(patchInfoRepository.updatePatchInfo(patchInfo.copy(updatedAt = DateTime.now(), mode = mode))) { "update patch info is failed" }
    }.isSuccess
}

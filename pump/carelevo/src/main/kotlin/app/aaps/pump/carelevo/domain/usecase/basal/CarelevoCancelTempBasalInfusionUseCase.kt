package app.aaps.pump.carelevo.domain.usecase.basal

import app.aaps.pump.carelevo.domain.model.infusion.derivePatchMode
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoCancelTempBasalInfusionUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    /**
     * Persist a canceled temp basal (delete the temp-basal infusion-info + recompute the patch mode from the
     * remaining infusion state) after a successful cancel-temp-basal write. Returns false with no
     * infusion/patch record or if any write fails.
     */
    fun persistTempBasalCancelled(): Boolean = runCatching {
        require(infusionInfoRepository.deleteTempBasalInfusionInfo()) { "delete temp basal infusion info is failed" }

        val infusionInfo = infusionInfoRepository.getInfusionInfoBySync()
            ?: throw NullPointerException("infusion info must be not null")

        val mode = infusionInfo.derivePatchMode()
            ?: throw NullPointerException("infusion info must be not null")

        val patchInfo = patchInfoRepository.getPatchInfoBySync()
            ?: throw NullPointerException("patch info must be not null")
        require(patchInfoRepository.updatePatchInfo(patchInfo.copy(updatedAt = DateTime.now(), mode = mode))) { "update patch info is failed" }
    }.isSuccess
}

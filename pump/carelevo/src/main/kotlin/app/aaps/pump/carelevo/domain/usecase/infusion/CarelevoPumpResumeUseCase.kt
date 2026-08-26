package app.aaps.pump.carelevo.domain.usecase.infusion

import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoPumpResumeUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    /**
     * Persist the resumed state (basalInfusionInfo `mode=1,isStop=false` + patchInfo `isStopped=false, …`)
     * after a successful pump-resume write on the BLE stack. Returns `false` on any missing record or failed
     * write.
     */
    fun persistResumed(): Boolean {
        val basalInfusionInfo = infusionInfoRepository.getInfusionInfoBySync()?.basalInfusionInfo ?: return false
        if (!infusionInfoRepository.updateBasalInfusionInfo(basalInfusionInfo.copy(updatedAt = DateTime.now(), mode = 1, isStop = false))) return false
        val patchInfo = patchInfoRepository.getPatchInfoBySync() ?: return false
        return patchInfoRepository.updatePatchInfo(
            patchInfo.copy(isStopped = false, stopMinutes = null, stopMode = null, isForceStopped = null, mode = 1)
        )
    }
}

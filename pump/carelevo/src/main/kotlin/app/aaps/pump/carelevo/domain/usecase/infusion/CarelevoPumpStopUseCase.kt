package app.aaps.pump.carelevo.domain.usecase.infusion

import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoPumpStopUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    /**
     * Persist the suspended state (basalInfusionInfo `mode=0,isStop=true` + patchInfo `isStopped=true, …`)
     * after a successful pump-stop write on the BLE stack. Returns `false` on any missing record or failed
     * write.
     */
    fun persistStopped(durationMin: Int): Boolean {
        val basalInfusionInfo = infusionInfoRepository.getInfusionInfoBySync()?.basalInfusionInfo ?: return false
        if (!infusionInfoRepository.updateBasalInfusionInfo(basalInfusionInfo.copy(updatedAt = DateTime.now(), mode = 0, isStop = true))) return false
        val patchInfo = patchInfoRepository.getPatchInfoBySync() ?: return false
        return patchInfoRepository.updatePatchInfo(
            patchInfo.copy(updatedAt = DateTime.now(), isStopped = true, stopMinutes = durationMin, stopMode = 0, isForceStopped = false, mode = 0)
        )
    }
}

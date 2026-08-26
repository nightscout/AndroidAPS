package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoPatchSafetyCheckUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository
) {

    /**
     * Persist the safety-check pass (`checkSafety=true`) after the BLE safety-check succeeds. Returns false
     * with no patch record or on a failed write.
     */
    fun persistSafetyChecked(): Boolean {
        val patchInfo = patchInfoRepository.getPatchInfoBySync() ?: return false
        return patchInfoRepository.updatePatchInfo(patchInfo.copy(checkSafety = true, updatedAt = DateTime.now()))
    }
}

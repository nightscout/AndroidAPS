package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import kotlin.time.Clock
import dev.zacsweers.metro.Inject

class CarelevoPatchNeedleInsertionCheckUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository
) {

    /**
     * Persist the cannula-insertion outcome: `checkNeedle=true` on [success], else `checkNeedle=false` +
     * `needleFailedCount++`. Returns false with no patch record or on a failed write.
     */
    fun persistNeedleResult(success: Boolean): Boolean {
        val patchInfo = patchInfoRepository.getPatchInfoBySync() ?: return false
        return if (success) {
            patchInfoRepository.updatePatchInfo(patchInfo.copy(updatedAt = Clock.System.now(), checkNeedle = true))
        } else {
            val nextFailedCount = (patchInfo.needleFailedCount ?: 0) + 1
            patchInfoRepository.updatePatchInfo(patchInfo.copy(updatedAt = Clock.System.now(), checkNeedle = false, needleFailedCount = nextFailedCount))
        }
    }
}

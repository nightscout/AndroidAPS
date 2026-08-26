package app.aaps.pump.carelevo.domain.usecase.userSetting

import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoUpdateMaxBolusDoseUseCase @Inject constructor(
    private val infusionInfoRepository: CarelevoInfusionInfoRepository,
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository
) {

    /** True if an immediate or extended bolus is in progress — a setting must not be pushed mid-bolus. */
    fun isBolusRunning(): Boolean {
        val infusionInfo = infusionInfoRepository.getInfusionInfoBySync()
        return infusionInfo?.immeBolusInfusionInfo != null || infusionInfo?.extendBolusInfusionInfo != null
    }

    /**
     * Persist the max-bolus setting. [synced] = the patch confirmed the push; when false,
     * `needMaxBolusDoseSyncPatch` stays true so the deferred-sync re-pushes on the next reconnect.
     * Returns false with no setting record or on a failed write.
     */
    fun persistMaxBolusDose(value: Double, synced: Boolean): Boolean {
        val userSettingInfo = userSettingInfoRepository.getUserSettingInfoBySync() ?: return false
        return userSettingInfoRepository.updateUserSettingInfo(
            userSettingInfo.copy(updatedAt = DateTime.now(), maxBolusDose = value, needMaxBolusDoseSyncPatch = !synced)
        )
    }
}

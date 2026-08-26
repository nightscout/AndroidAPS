package app.aaps.pump.carelevo.domain.usecase.userSetting

import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoUpdateLowInsulinNoticeAmountUseCase @Inject constructor(
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository
) {

    /**
     * Persist the low-insulin-notice setting. [synced] = the patch confirmed the push; when false,
     * `needLowInsulinNoticeAmountSyncPatch` stays true so the deferred-sync re-pushes on reconnect. Returns
     * false with no setting record or on a failed write.
     */
    fun persistLowInsulinNoticeAmount(value: Int, synced: Boolean): Boolean {
        val userSettingInfo = userSettingInfoRepository.getUserSettingInfoBySync() ?: return false
        return userSettingInfoRepository.updateUserSettingInfo(
            userSettingInfo.copy(updatedAt = DateTime.now(), lowInsulinNoticeAmount = value, needLowInsulinNoticeAmountSyncPatch = !synced)
        )
    }
}

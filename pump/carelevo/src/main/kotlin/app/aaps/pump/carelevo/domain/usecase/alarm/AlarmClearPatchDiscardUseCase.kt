package app.aaps.pump.carelevo.domain.usecase.alarm

import app.aaps.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import org.joda.time.DateTime

class AlarmClearPatchDiscardUseCase(
    private val alarmRepository: CarelevoAlarmInfoRepository,
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    /**
     * Persist the alarm-triggered discard outcome after a successful discard write: ack the alarm, clear
     * the user-setting sync flags, and delete the infusion + patch records. Returns false with no
     * user-setting record or if any write fails.
     */
    fun persistAlarmDiscarded(alarmId: String): Boolean = runCatching {
        alarmRepository.removeAlarm(alarmId).blockingAwait()

        val userSettingInfo = userSettingInfoRepository.getUserSettingInfoBySync()
            ?: throw NullPointerException("user setting info must be not null")
        require(
            userSettingInfoRepository.updateUserSettingInfo(
                userSettingInfo.copy(updatedAt = DateTime.now(), needMaxBolusDoseSyncPatch = false, needMaxBasalSpeedSyncPatch = false, needLowInsulinNoticeAmountSyncPatch = false)
            )
        ) { "update user setting info is failed" }
        require(infusionInfoRepository.deleteInfusionInfo()) { "delete infusion info is failed" }
        require(patchInfoRepository.deletePatchInfo()) { "delete patch info is failed" }
    }.isSuccess
}

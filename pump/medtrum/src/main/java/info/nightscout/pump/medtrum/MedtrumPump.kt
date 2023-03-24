package info.nightscout.pump.medtrum

import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.profile.Instantiator
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileStore
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.pump.medtrum.comm.enums.AlarmSetting
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.pump.medtrum.extension.toByteArray
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.round

@Singleton
class MedtrumPump @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val dateUtil: DateUtil,
    private val instantiator: Instantiator
) {

    enum class PatchActivationState(val state: Int) {
        NONE(0),
        IDLE(1),
        ACTIVATING(2),
        ACTIVATED(3),
        DEACTIVATING(4),
        DEACTIVATED(5),
        ERROR(6)
    }

    // Pump state and parameters
    var pumpState = MedtrumPumpState.NONE // TODO save in SP
    var patchActivationState = PatchActivationState.NONE // TODO save in SP

    // TODO set these setting on init
    // User settings (desired values, to be set on pump)
    var desiredPatchExpiration = false
    var desiredAlarmSetting = AlarmSetting.LIGHT_VIBRATE_AND_BEEP.code
    var desiredHourlyMaxInsulin: Int = 40
    var desiredDailyMaxInsulin: Int = 180

    // User settings (actual value's as reported by pump)

    // Alarm settings

    // Pump status

    var patchId = 0L
    var lastTimeReceivedFromPump = 0L // Time in seconds!

    // Pump history

    // Last basal status update
    var lastBasalType = 0
    var lastBasalRate = 0.0
    var lastBasalSequence = 0
    var lastBasalPatchId = 0
    var lastBasalStartTime = 0L

    // Last stop status update
    var lastStopSequence = 0
    var lastStopPatchId = 0

    fun buildMedtrumProfileArray(nsProfile: Profile): ByteArray? {
        val list = nsProfile.getBasalValues()
        var basals = byteArrayOf()
        for (item in list) {
            val rate = round(item.value / 0.05).toInt()
            val time = item.timeAsSeconds / 60
            if (rate > 0xFFF || time > 0xFFF) {
                aapsLogger.error(LTag.PUMP, "buildMedtrumProfileArray: rate or time too large: $rate, $time")
                return null
            }
            basals += ((rate shl 12) + time).toByteArray(3)
            aapsLogger.debug(LTag.PUMP, "buildMedtrumProfileArray: value: ${item.value} time: ${item.timeAsSeconds}")
        }
        return (list.size).toByteArray(1) + basals
    }

    fun handleBasalStatusUpdate(basalType: Int, basalValue: Double, basalSequence: Int, basalPatchId: Int, basalStartTime: Long) {
        handleBasalStatusUpdate(basalType, basalValue, basalSequence, basalPatchId, basalStartTime, dateUtil.now())
    }

    fun handleBasalStatusUpdate(basalType: Int, basalRate: Double, basalSequence: Int, basalPatchId: Int, basalStartTime: Long, receivedTime: Long) {
        aapsLogger.debug(
            LTag.PUMP, "handleBasalStatusUpdate: basalType: $basalType basalValue: $basalRate basalSequence: $basalSequence basalPatchId: $basalPatchId basalStartTime: $basalStartTime " +
                "receivedTime: $receivedTime"
        )
        lastBasalType = basalType
        lastBasalRate = basalRate
        lastBasalSequence = basalSequence
        lastBasalPatchId = basalPatchId
        lastBasalStartTime = basalStartTime
    }

    fun handleStopStatusUpdate(stopSequence: Int, stopPatchId: Int) {
        aapsLogger.debug(LTag.PUMP, "handleStopStatusUpdate: stopSequence: $stopSequence stopPatchId: $stopPatchId")
        lastStopSequence = stopSequence
        lastStopPatchId = stopPatchId
    }
}

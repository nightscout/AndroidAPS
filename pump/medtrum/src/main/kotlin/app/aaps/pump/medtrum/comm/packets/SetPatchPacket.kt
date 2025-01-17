package app.aaps.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import app.aaps.pump.medtrum.MedtrumPump
import app.aaps.pump.medtrum.comm.enums.AlarmSetting
import app.aaps.pump.medtrum.comm.enums.CommandType.SET_PATCH
import app.aaps.pump.medtrum.extension.toByte
import app.aaps.pump.medtrum.extension.toByteArray
import javax.inject.Inject
import kotlin.math.round

class SetPatchPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    @Inject lateinit var medtrumPump: MedtrumPump

    init {
        opCode = SET_PATCH.code
    }

    override fun getRequest(): ByteArray {
        /**
         * byte 0: opCode
         * byte 1: alarmSetting                    // See AlarmSetting
         * byte 2-3: hourlyMaxInsulin              // Max hourly dose of insulin, divided by 0.05
         * byte 4-5: dailyMaxSet                   // Max daily dose of insulin, divided by 0.05
         * byte 6: expirationTimer                 // Expiration timer, 0 = no expiration 1 = 12 hour reminder and expiration
         * byte 7: autoSuspendEnable               // Value for auto mode, not used for AAPS
         * byte 8: autoSuspendTime                 // Value for auto mode, not used for AAPS
         * byte 9: lowSuspend                      // Value for auto mode, not used for AAPS
         * byte 10: predictiveLowSuspend           // Value for auto mode, not used for AAPS
         * byte 11: predictiveLowSuspendRange      // Value for auto mode, not used for AAPS
         */

        val alarmSetting: AlarmSetting = medtrumPump.desiredAlarmSetting
        val hourlyMaxInsulin: Int = round(medtrumPump.desiredHourlyMaxInsulin / 0.05).toInt()
        val dailyMaxInsulin: Int = round(medtrumPump.desiredDailyMaxInsulin / 0.05).toInt()
        val patchExpiration: Byte = medtrumPump.desiredPatchExpiration.toByte()
        val autoSuspendEnable: Byte = 0
        val autoSuspendTime: Byte = 12 // Not sure why, but pump needs this
        val lowSuspend: Byte = 0
        val predictiveLowSuspend: Byte = 0
        val predictiveLowSuspendRange: Byte = 30 // Not sure why, but pump needs this

        return byteArrayOf(opCode) + alarmSetting.code + hourlyMaxInsulin.toByteArray(2) + dailyMaxInsulin.toByteArray(2) + patchExpiration + autoSuspendEnable + autoSuspendTime + lowSuspend + predictiveLowSuspend + predictiveLowSuspendRange
    }
}

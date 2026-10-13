package app.aaps.pump.medtrum.comm.packets

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.pump.medtrum.MedtrumPump
import app.aaps.pump.medtrum.comm.enums.BasalType
import app.aaps.pump.medtrum.comm.enums.CommandType.ACTIVATE
import app.aaps.pump.medtrum.extension.toByte
import app.aaps.pump.medtrum.extension.toByteArray
import app.aaps.pump.medtrum.extension.toInt
import app.aaps.pump.medtrum.extension.toLong
import app.aaps.pump.medtrum.util.MedtrumTimeUtil
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import kotlin.math.round

class ActivatePacket(injector: HasAndroidInjector, private val basalProfile: ByteArray) : MedtrumPacket(injector) {

    @Inject lateinit var medtrumPump: MedtrumPump
    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var medtrumTimeUtil: MedtrumTimeUtil

    companion object {

        private const val RESP_PATCH_ID_START = 6
        private const val RESP_PATCH_ID_END = RESP_PATCH_ID_START + 4
        private const val RESP_TIME_START = 10
        private const val RESP_TIME_END = RESP_TIME_START + 4
        private const val RESP_BASAL_TYPE_START = 14
        private const val RESP_BASAL_TYPE_END = RESP_BASAL_TYPE_START + 1
        private const val RESP_BASAL_VALUE_START = 15
        private const val RESP_BASAL_VALUE_END = RESP_BASAL_VALUE_START + 2
        private const val RESP_BASAL_SEQUENCE_START = 17
        private const val RESP_BASAL_SEQUENCE_END = RESP_BASAL_SEQUENCE_START + 2
        private const val RESP_BASAL_PATCH_ID_START = 19
        private const val RESP_BASAL_PATCH_ID_END = RESP_BASAL_PATCH_ID_START + 2
        private const val RESP_BASAL_START_TIME_START = 21
        private const val RESP_BASAL_START_TIME_END = RESP_BASAL_START_TIME_START + 4
    }

    init {
        opCode = ACTIVATE.code
        expectedMinRespLength = RESP_BASAL_START_TIME_END
    }

    override fun getRequest(): ByteArray {
        /**
         * byte 0: opCode
         * byte 1: autoSuspendEnable         // Value for auto mode, not used for AAPS
         * byte 2: autoSuspendTime           // Value for auto mode, not used for AAPS
         * byte 3: expirationTimer           // Expiration timer, 0 = no expiration 1 = 12 hour reminder and expiration after 3 days
         * byte 4: alarmSetting              // See AlarmSetting
         * byte 5: lowSuspend                // Value for auto mode, not used for AAPS
         * byte 6: predictiveLowSuspend      // Value for auto mode, not used for AAPS
         * byte 7: predictiveLowSuspendRange // Value for auto mode, not used for AAPS
         * byte 8-9: hourlyMaxInsulin        // Max hourly dose of insulin, divided by 0.05
         * byte 10-11: dailyMaxSet           // Max daily dose of insulin, divided by 0.05
         * byte 12-13: tddToday              // Current TDD (of present day), divided by 0.05
         * byte 14: 1                        // Always 1
         * bytes 15 - end                    // Basal profile > see MedtrumPump
         */

        val autoSuspendEnable: Byte = 0
        val autoSuspendTime: Byte = 12 // Not sure why, but pump needs this in order to activate

        val patchExpiration: Byte = medtrumPump.desiredPatchExpiration.toByte()
        val alarmSetting: Byte = medtrumPump.desiredAlarmSetting.code

        val lowSuspend: Byte = 0
        val predictiveLowSuspend: Byte = 0
        val predictiveLowSuspendRange: Byte = 30 // Not sure why, but pump needs this in order to activate

        val hourlyMaxInsulin: Int = round(medtrumPump.desiredHourlyMaxInsulin / 0.05).toInt()
        val dailyMaxInsulin: Int = round(medtrumPump.desiredDailyMaxInsulin / 0.05).toInt()
        val currentTDD: Double = tddCalculator.calculateToday()?.totalAmount?.div(0.05) ?: 0.0

        return byteArrayOf(opCode) + autoSuspendEnable + autoSuspendTime + patchExpiration + alarmSetting + lowSuspend + predictiveLowSuspend + predictiveLowSuspendRange + hourlyMaxInsulin.toByteArray(
            2
        ) + dailyMaxInsulin.toByteArray(2) + currentTDD.toInt().toByteArray(2) + 1.toByte() + basalProfile
    }

    override fun handleResponse(data: ByteArray): Boolean {
        val success = super.handleResponse(data)
        if (success) {
            val patchId = data.copyOfRange(RESP_PATCH_ID_START, RESP_PATCH_ID_END).toLong()
            val time = medtrumTimeUtil.convertPumpTimeToSystemTimeMillis(data.copyOfRange(RESP_TIME_START, RESP_TIME_END).toLong())
            val basalType = enumValues<BasalType>()[data.copyOfRange(RESP_BASAL_TYPE_START, RESP_BASAL_TYPE_END).toInt()]
            val basalValue = data.copyOfRange(RESP_BASAL_VALUE_START, RESP_BASAL_VALUE_END).toInt() * 0.05
            val basalSequence = data.copyOfRange(RESP_BASAL_SEQUENCE_START, RESP_BASAL_SEQUENCE_END).toInt()
            val basalPatchId = data.copyOfRange(RESP_BASAL_PATCH_ID_START, RESP_BASAL_PATCH_ID_END).toLong()
            val basalStartTime = medtrumTimeUtil.convertPumpTimeToSystemTimeMillis(data.copyOfRange(RESP_BASAL_START_TIME_START, RESP_BASAL_START_TIME_END).toLong())

            medtrumPump.lastTimeReceivedFromPump = time
            medtrumPump.handleNewPatch(patchId, basalSequence, System.currentTimeMillis())

            // Update the actual basal profile
            medtrumPump.actualBasalProfile = basalProfile
            medtrumPump.handleBasalStatusUpdate(basalType, basalValue, basalSequence, basalPatchId, basalStartTime, time)
        }

        return success
    }
}

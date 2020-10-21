package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRS_Packet_Bolus_Set_Step_Bolus_Start(
    injector: HasAndroidInjector,
    private var amount: Double = 0.0,
    private var speed: Int = 0
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var constraintChecker: ConstraintChecker

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_START
        // Speed 0 => 12 sec/U, 1 => 30 sec/U, 2 => 60 sec/U
        // HARDCODED LIMIT - if there is one that could be created
        amount = constraintChecker.applyBolusConstraints(Constraint(amount)).value()
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus start : $amount speed: $speed")
    }

    override fun getRequestParams(): ByteArray {
        val stepBolusRate = (amount * 100).toInt()
        val request = ByteArray(3)
        request[0] = (stepBolusRate and 0xff).toByte()
        request[1] = (stepBolusRate ushr 8 and 0xff).toByte()
        request[2] = (speed and 0xff).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        danaPump.bolusStartErrorCode = intFromBuff(data, 0, 1)
        if (danaPump.bolusStartErrorCode == 0) {
            failed = false
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
        } else {
            aapsLogger.error("Result Error: ${danaPump.bolusStartErrorCode}")
            failed = true
        }
    }

    override fun getFriendlyName(): String {
        return "BOLUS__SET_STEP_BOLUS_START"
    }
}
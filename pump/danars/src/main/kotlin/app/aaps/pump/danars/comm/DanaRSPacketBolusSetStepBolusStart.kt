package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketBolusSetStepBolusStart @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump,
    private val constraintChecker: ConstraintsChecker
) : DanaRSPacket() {

    private var amount: Double = 0.0
    private var speed: Int = 0

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_START
    }

    fun with(amount: Double, speed: Int) = this.also {
        it.amount = amount
        it.speed = speed
        // Speed 0 => 12 sec/U, 1 => 30 sec/U, 2 => 60 sec/U
        // HARDCODED LIMIT - if there is one that could be created
        it.amount = constraintChecker.applyBolusConstraints(ConstraintObject(it.amount, aapsLogger)).value()
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus start : ${it.amount} speed: $speed")
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

    override val friendlyName: String = "BOLUS__SET_STEP_BOLUS_START"
}
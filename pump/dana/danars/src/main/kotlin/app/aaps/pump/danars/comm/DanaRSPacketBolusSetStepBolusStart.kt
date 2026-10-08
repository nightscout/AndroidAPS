package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import dev.zacsweers.metro.Inject
import kotlin.math.roundToInt

@Inject
class DanaRSPacketBolusSetStepBolusStart(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump
) : DanaRSPacket() {

    private var amount: Double = 0.0
    private var speed: Int = 0
    private var algorithm: Boolean = false

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_START
    }

    /**
     * @param algorithm true when the loop sends the bolus (SMB). Only Dana-i2 receives it, and writes
     * it into the STEP_BOLUS_COMMAND history record.
     */
    fun with(amount: Double, speed: Int, algorithm: Boolean) = this.also {
        it.amount = amount
        it.speed = speed
        it.algorithm = algorithm
        // Speed 0 => 12 sec/U, 1 => 30 sec/U, 2 => 60 sec/U
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus start : ${it.amount} speed: $speed algorithm: $algorithm")
    }

    override fun getRequestParams(): ByteArray {
        // Round, not truncate: 2.55 * 100 is 254.99999 and toInt() would send 2.54 U
        val stepBolusRate = (amount * 100).roundToInt()
        // Dana-i2 does not answer the 3 byte request. It needs the command type as the 4th byte.
        val request = ByteArray(if (danaPump.isDanaI2) 4 else 3)
        request[0] = (stepBolusRate and 0xff).toByte()
        request[1] = (stepBolusRate ushr 8 and 0xff).toByte()
        request[2] = (speed and 0xff).toByte()
        if (danaPump.isDanaI2) request[3] = (if (algorithm) COMMAND_TYPE_ALGORITHM else COMMAND_TYPE_NORMAL).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        // Dana-i2 answers with 2 bytes of error flags (see DanaRSPlugin.bolusStartErrorText).
        // Reading only 1 byte would take Bolus Max (0x0100) or Remain < Bolus (0x0200) as OK.
        danaPump.bolusStartErrorCode = intFromBuff(data, 0, if (danaPump.isDanaI2) 2 else 1)
        if (danaPump.bolusStartErrorCode == 0) {
            failed = false
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
        } else {
            aapsLogger.error("Result Error: 0x%04X".format(danaPump.bolusStartErrorCode))
            failed = true
        }
    }

    override val friendlyName: String = "BOLUS__SET_STEP_BOLUS_START"

    companion object {

        const val COMMAND_TYPE_NORMAL = 0
        const val COMMAND_TYPE_ALGORITHM = 1
    }
}

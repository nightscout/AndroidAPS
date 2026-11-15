package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import dev.zacsweers.metro.Inject

@Inject
class DanaRSPacketNotifyDeliveryRateDisplay(
    private val aapsLogger: AAPSLogger,
    private val ch: ConcentrationHelper,
    private val bolusProgressData: BolusProgressData,
    private val danaPump: DanaPump
) : DanaRSPacket() {

    init {
        type = BleEncryption.DANAR_PACKET__TYPE_NOTIFY
        opCode = BleEncryption.DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_RATE_DISPLAY
    }

    override fun handleMessage(data: ByteArray) {
        val deliveredInsulin = byteArrayToInt(getBytes(data, DATA_START, 2)) / 100.0
        // The pump can go on with a bolus the driver no longer follows, for example after a stop that got no answer.
        // That progress used to end the whole app here. The pump history counts that bolus.
        val bolusing = danaPump.bolusingDetailedBolusInfo
        if (bolusing == null) {
            aapsLogger.debug(LTag.PUMPCOMM, "Delivered insulin so far: $deliveredInsulin, of a bolus the driver does not follow")
            return
        }
        danaPump.bolusProgressLastTimeStamp = System.currentTimeMillis()
        failed = deliveredInsulin < bolusing.insulin
        bolusProgressData.updateProgress(PumpInsulin(deliveredInsulin))
        aapsLogger.debug(LTag.PUMPCOMM, "Delivered insulin so far: $deliveredInsulin")
    }

    override val friendlyName: String = "NOTIFY__DELIVERY_RATE_DISPLAY"
}
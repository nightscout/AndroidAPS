package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject
import kotlin.math.min

class DanaRSPacketNotifyDeliveryComplete @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val ch: ConcentrationHelper,
    private val bolusProgressData: BolusProgressData,
    private val danaPump: DanaPump
) : DanaRSPacket() {

    init {
        type = BleEncryption.DANAR_PACKET__TYPE_NOTIFY
        opCode = BleEncryption.DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_COMPLETE
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        val deliveredInsulin = byteArrayToInt(getBytes(data, DATA_START, 2)) / 100.0
        danaPump.bolusDone = true
        val isPriming = bolusProgressData.state.value?.isPriming == true
        val insulin = bolusProgressData.state.value?.insulin ?: 0.0
        val delivered = ch.fromPump(PumpInsulin(deliveredInsulin), isPriming)
        val percent = if (insulin > 0) min((delivered / insulin * 100).toInt(), 100) else 0
        val status = ch.bolusProgressString(PumpInsulin(deliveredInsulin), isPriming)
        bolusProgressData.updateProgress(percent, status, deliveredInsulin)
        aapsLogger.debug(LTag.PUMPCOMM, "Delivered insulin: $deliveredInsulin")
    }

    override val friendlyName: String = "NOTIFY__DELIVERY_COMPLETE"
}
package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketGeneralGetPumpCheck @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump,
    private val notificationManager: NotificationManager
) : DanaRSPacket() {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_PUMP_CHECK
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        if (data.size < 5) {
            failed = true
            return
        } else
            failed = false
        var dataIndex = DATA_START
        var dataSize = 1
        danaPump.hwModel = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaPump.protocol = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaPump.productCode = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        aapsLogger.debug(LTag.PUMPCOMM, "Model: " + String.format("%02X ", danaPump.hwModel))
        aapsLogger.debug(LTag.PUMPCOMM, "Protocol: " + String.format("%02X ", danaPump.protocol))
        aapsLogger.debug(LTag.PUMPCOMM, "Product Code: " + String.format("%02X ", danaPump.productCode))
        if (danaPump.productCode < 2) {
            notificationManager.post(NotificationId.UNSUPPORTED_FIRMWARE, app.aaps.pump.dana.R.string.unsupportedfirmware)
        }
    }

    override val friendlyName: String = "REVIEW__GET_PUMP_CHECK"
}

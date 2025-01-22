package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.Utils

class CmdDevicesGet(
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    init {
        port = "0000"
    }

    override fun getFirstData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x02, 0x00)
        val data = Utils.concat(indexByte, data2)
        pumpReqIndex++
        return data
    }

    override fun getNextData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x00, 0x00, 0x01)
        val data = Utils.concat(indexByte, data2)
        pumpReqIndex++
        return data
    }

    override fun decodeConfirmData(data: ByteArray) {
        val value = Utils.bytesToInt(data[7], data[6])

        val firmwareVersion = data[18].toString() + "." + data[19]
        aapsLogger.debug(
            LTag.PUMPCOMM, "CmdGetDevices====" +
                Utils.bytesToHex(data) + "=====" + value + "===" + firmwareVersion
        )
        equilManager.setFirmwareVersion(firmwareVersion)
        synchronized(this) {
            cmdStatus = true
            (this as Object).notify()
        }
    }

    override fun getEventType(): EquilHistoryRecord.EventType? = EquilHistoryRecord.EventType.READ_DEVICES
}

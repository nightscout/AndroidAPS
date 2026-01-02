package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.Utils

class CmdModelSet(
    var mode: Int,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    fun getMode(): RunMode =
        when (mode) {
            0    -> RunMode.SUSPEND
            1    -> RunMode.RUN
            2    -> RunMode.RUN
            else -> RunMode.SUSPEND
        }

    override fun getFirstData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x01, 0x00)
        val data3 = Utils.intToBytes(mode)
        val data = Utils.concat(indexByte, data2, data3)
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
        synchronized(this) {
            cmdSuccess = true
            (this as Object).notify()
        }
    }

    override fun getEventType(): EquilHistoryRecord.EventType? {
        val runMode = getMode()
        if (runMode == RunMode.RUN) {
            return EquilHistoryRecord.EventType.RESUME_DELIVERY
        } else if (runMode == RunMode.SUSPEND) {
            return EquilHistoryRecord.EventType.SUSPEND_DELIVERY
        }
        return null
    }
}

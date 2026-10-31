package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.notifyAll
import app.aaps.pump.equil.data.AlarmMode
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.Utils

class CmdAlarmSet(
    var mode: Int,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    override fun getFirstData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x01, 0x0b)
        val data3 = Utils.intToBytes(mode)
        val data = Utils.concat(indexByte, data2, data3)
        pumpReqIndex++
        return data
    }

    override fun getNextData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x00, 0x0b, 0x01)
        val data = Utils.concat(indexByte, data2)
        pumpReqIndex++
        return data
    }

    override fun decodeConfirmData(data: ByteArray) {
        synchronized(this) {
            cmdSuccess = true
            notifyAll()
        }
    }

    override fun getEventType(): EquilHistoryRecord.EventType? =
        when (mode) {
            AlarmMode.MUTE.command           -> EquilHistoryRecord.EventType.SET_ALARM_MUTE
            AlarmMode.TONE.command           -> EquilHistoryRecord.EventType.SET_ALARM_TONE
            AlarmMode.TONE_AND_SHAKE.command -> EquilHistoryRecord.EventType.SET_ALARM_TONE_AND_SHAK
            AlarmMode.SHAKE.command          -> EquilHistoryRecord.EventType.SET_ALARM_SHAKE
            else                             -> null
        }
}

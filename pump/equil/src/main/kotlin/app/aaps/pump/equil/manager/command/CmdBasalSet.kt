package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.driver.definition.BasalSchedule
import app.aaps.pump.equil.keys.EquilBooleanKey
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.Utils

class CmdBasalSet(
    var basalSchedule: BasalSchedule,
    var profile: Profile,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    override fun getFirstData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x01, 0x02)
        val list: MutableList<Byte?> = ArrayList<Byte?>()
        var i = 0
        for (basalScheduleEntry in basalSchedule.getEntries()) {
            val rate = basalScheduleEntry.rate
            val value = rate / 2f
            val bs = Utils.basalToByteArray(value)
            aapsLogger.debug(
                LTag.PUMPCOMM,
                (i.toString() + "==CmdBasalSet==" + value + "====" + rate + "===" + Utils.decodeSpeedToUH(value) + "==="
                    + Utils.decodeSpeedToUHT(value))
            )
            list.add(bs[1])
            list.add(bs[0])
            list.add(bs[1])
            list.add(bs[0])
            i++
        }
        val hex = Utils.bytesToHex(list)
        val data = Utils.concat(indexByte, data2, Utils.hexStringToBytes(hex))
        pumpReqIndex++
        return data
    }

    override fun getNextData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x00, 0x02, 0x01)
        val data = Utils.concat(indexByte, data2)
        pumpReqIndex++
        return data
    }

    override fun decodeConfirmData(data: ByteArray) {
        synchronized(this) {
            preferences.put(EquilBooleanKey.BasalSet, true)
            cmdSuccess = true
            (this as Object).notifyAll()
        }
    }

    override fun getEventType(): EquilHistoryRecord.EventType? {
        return EquilHistoryRecord.EventType.SET_BASAL_PROFILE
    }
}

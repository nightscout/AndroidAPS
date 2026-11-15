package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.notify
import app.aaps.pump.equil.EquilConst
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.Utils
import kotlin.math.roundToInt

/**
 * Bolus. The pump takes the amount ([step], 160 per unit) and the duration in seconds ([stepTime]), so
 * the duration sets the bolus speed: [secondsPerUnit] 40 = 1.5 U/min (standard).
 */
class CmdLargeBasalSet(
    var insulin: Double,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager,
    secondsPerUnit: Int = EquilConst.EQUIL_BOLUS_SECONDS_PER_UNIT_STANDARD
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    var step: Int = 0
    var stepTime: Int = 0

    init {
        if (insulin != 0.0) {
            step = (insulin / 0.05 * 8).toInt()
            // Whole seconds, at least 1: a small bolus at a fast speed would otherwise be sent with 0 s.
            stepTime = (insulin * secondsPerUnit).roundToInt().coerceAtLeast(1)
        }
    }

    override fun getFirstData(): ByteArray {
        aapsLogger.debug(LTag.PUMPCOMM, "step===$step=====$stepTime")
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x01, 0x03)
        val data3 = Utils.intToBytes(step)
        val data4 = Utils.intToBytes(stepTime)
        val data5 = Utils.intToBytes(0)
        val data = Utils.concat(indexByte, data2, data3, data4, data5, data5)
        pumpReqIndex++
        return data
    }

    override fun getNextData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x00, 0x03, 0x01)
        val data3 = Utils.intToBytes(0)
        val data = Utils.concat(indexByte, data2, data3)
        pumpReqIndex++
        return data
    }

    override fun decodeConfirmData(data: ByteArray) {
        // val index = data[4].toInt()
        // val status = data[6].toInt() and 0xff
        synchronized(this) {
            cmdSuccess = true
            notify()
        }
    }

    override fun getEventType(): EquilHistoryRecord.EventType? =
        if (insulin == 0.0) EquilHistoryRecord.EventType.CANCEL_BOLUS
        else EquilHistoryRecord.EventType.SET_BOLUS
}

package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.notifyAll
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.Utils
import java.math.BigDecimal

/**
 * Reads the basal the pump runs (#2454). It answers with its 48 half hours, each the insulin of that half hour in
 * 0.00625 U steps, two bytes with the low byte first, the same as [CmdBasalSet] sends them.
 */
class CmdBasalGet(
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    /** The insulin of each hour of the day from midnight, in U/h, as the pump reported it. null until it did. */
    var hourlyRates: List<Double>? = null
        private set

    init {
        // A read on the open link answers in about a second. Without an answer the profile set must not wait 22 s.
        timeOut = 8000
    }

    override fun getFirstData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x02, 0x02)
        val data = Utils.concat(indexByte, data2)
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
        aapsLogger.debug(LTag.PUMPCOMM, "CmdBasalGet==" + Utils.bytesToHex(data))
        // Too short for the 48 half hours: an ack, not the answer. It neither fails the read nor replaces a table
        // read before; without a table the command times out and the read counts as failed.
        if (data.size < RATES_START + 2 * HALF_HOURS) return
        val rates = (0 until 24).map { hour -> STEP.multiply(BigDecimal(halfHour(data, 2 * hour) + halfHour(data, 2 * hour + 1))).toDouble() }
        aapsLogger.debug(LTag.PUMPCOMM, "CmdBasalGet hourly rates: $rates")
        synchronized(this) {
            hourlyRates = rates
            cmdSuccess = true
            notifyAll()
        }
    }

    private fun halfHour(data: ByteArray, index: Int): Int =
        Utils.bytesToInt(data[RATES_START + 2 * index + 1], data[RATES_START + 2 * index])

    override fun getEventType(): EquilHistoryRecord.EventType? = null

    companion object {

        private const val RATES_START = 6
        private const val HALF_HOURS = 48
        private val STEP = BigDecimal("0.00625")
    }
}

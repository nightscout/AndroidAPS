package app.aaps.pump.diaconn

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import kotlin.math.max

/**
 * After a status read, AAPS and the Diaconn G8 must agree on the temp basal, as the Dana drivers check it.
 *
 * A real G8 goes back to its temp basal when the user starts the insulin again after a stop on the pump, and keeps the
 * end it had (2026-11-11). AAPS had cut the temp basal at the stop, so it counted the base rate while the pump gave the
 * temp basal.
 */
object DiaconnTempBasalSync {

    /**
     * Brings the AAPS record in line with the pump status read at [now]. A stop of the pump is not compared here: its
     * own record (`PumpSuspendSync`) covers it.
     *
     * @param pumpRuns the pump runs a temp basal of [rate] U/h from [pumpStart] to [pumpEnd]
     * @param notBefore the end of the last stop on the pump, from its history, or 0 when not known. A temp basal the pump
     * runs is recorded only from there: the part before it is recorded already, or was the stop.
     */
    suspend fun followStatus(
        pumpSync: PumpSync,
        aapsLogger: AAPSLogger,
        pumpRuns: Boolean,
        pumpStart: Long,
        pumpEnd: Long,
        rate: Double,
        notBefore: Long,
        now: Long,
        serial: String
    ) {
        val recorded = pumpSync.expectedPumpState().temporaryBasal
        if (recorded?.type == PumpSync.TemporaryBasalType.PUMP_SUSPEND) return
        if (pumpRuns && recorded == null) {
            // With no stop known, only from now: an earlier start could lie over a record AAPS has already.
            val start = if (notBefore > 0) max(pumpStart, notBefore) else now
            if (start >= pumpEnd) return
            aapsLogger.debug(LTag.PUMP, "The pump runs a temp basal of $rate U/h that AAPS does not have, recording it from $start until $pumpEnd")
            pumpSync.syncTemporaryBasalWithPumpId(
                timestamp = start,
                rate = PumpRate(rate),
                duration = pumpEnd - start,
                isAbsolute = true,
                type = PumpSync.TemporaryBasalType.NORMAL,
                pumpId = start,
                pumpType = PumpType.DIACONN_G8,
                pumpSerial = serial
            )
        } else if (!pumpRuns && recorded != null) {
            aapsLogger.debug(LTag.PUMP, "AAPS has a temp basal the pump does not run, ending it now")
            pumpSync.syncStopTemporaryBasalWithPumpId(timestamp = now, endPumpId = now, pumpType = PumpType.DIACONN_G8, pumpSerial = serial)
        }
    }
}

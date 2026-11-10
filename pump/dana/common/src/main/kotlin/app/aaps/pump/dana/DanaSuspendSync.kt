package app.aaps.pump.dana

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync

/**
 * AAPS records a stop of the insulin on a Dana pump (the user suspends it on the pump) as a 0 U/h temp basal of type
 * PUMP_SUSPEND, as other drivers do. Without it AAPS counted the basal the pump did not give, so IOB was too high.
 *
 * The pump history gives the exact start and end (Dana RS, DanaR v2). The status read only tells whether the pump is
 * stopped now: it is the only source on the DanaR and the Korean DanaR, which read no history, and it ends a record
 * whose end the history did not give.
 */
object DanaSuspendSync {

    /** Longer than any stop: the pump gives the end. The same as Equil. */
    private val MAX_DURATION = T.mins(4800).msecs()

    /** The pump history says the pump stopped the insulin at [time]. */
    suspend fun started(pumpSync: PumpSync, time: Long, pumpId: Long, pumpType: PumpType, serial: String): Boolean =
        pumpSync.syncTemporaryBasalWithPumpId(
            timestamp = time,
            rate = PumpRate(0.0),
            duration = MAX_DURATION,
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = serial
        )

    /** The pump history says the pump gives insulin again from [time]. */
    suspend fun ended(pumpSync: PumpSync, time: Long, pumpId: Long, pumpType: PumpType, serial: String): Boolean =
        pumpSync.syncStopTemporaryBasalWithPumpId(timestamp = time, endPumpId = pumpId, pumpType = pumpType, pumpSerial = serial)

    /**
     * Brings the record in line with the pump status read at [now]: [suspended] is what the pump says. The start or
     * the end is then [now], the best time known without history.
     */
    suspend fun followStatus(pumpSync: PumpSync, aapsLogger: AAPSLogger, suspended: Boolean, now: Long, pumpType: PumpType, serial: String) {
        val recorded = pumpSync.expectedPumpState().temporaryBasal?.type == PumpSync.TemporaryBasalType.PUMP_SUSPEND
        if (suspended && !recorded) {
            aapsLogger.debug(LTag.PUMP, "The pump is suspended, recording a 0 U/h PUMP_SUSPEND temp basal from now")
            started(pumpSync, now, now, pumpType, serial)
        } else if (!suspended && recorded) {
            aapsLogger.debug(LTag.PUMP, "The pump gives insulin again, ending the PUMP_SUSPEND temp basal now")
            ended(pumpSync, now, now, pumpType, serial)
        }
    }
}

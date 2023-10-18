package app.aaps.core.main.extensions

import app.aaps.core.data.db.GlucoseUnit
import app.aaps.core.data.db.IDs
import app.aaps.core.data.db.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.utils.DateUtil

fun TE.isOlderThan(hours: Double, dateUtil: DateUtil): Boolean {
    return getHoursFromStart(dateUtil) > hours
}

fun TE.getHoursFromStart(dateUtil: DateUtil): Double {
    return (dateUtil.now() - timestamp) / (60 * 60 * 1000.0)
}

fun TE.Companion.asAnnouncement(error: String, pumpId: Long? = null, pumpType: PumpType? = null, pumpSerial: String? = null): TE =
    TE(
        timestamp = System.currentTimeMillis(),
        type = TE.Type.ANNOUNCEMENT,
        duration = 0, note = error,
        enteredBy = "AAPS",
        glucose = null,
        glucoseType = null,
        glucoseUnit = GlucoseUnit.MGDL,
        ids = IDs(
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )
    )

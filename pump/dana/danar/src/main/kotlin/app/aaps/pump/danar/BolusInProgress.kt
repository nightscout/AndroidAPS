package app.aaps.pump.danar

import app.aaps.core.data.model.BS
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.dana.keys.DanaStringNonKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The bolus a DanaR or a Korean DanaR is giving. It is saved just before the start and removed when the bolus command
 * ends. So a saved bolus that a status read finds is one of an app that was killed during it.
 *
 * These drivers record a bolus only when the command ends, and they import no bolus history. When the app was killed
 * during a bolus, the pump went on and gave it all, but AAPS never recorded it, and IOB was too low by the whole bolus.
 */
@SingleIn(AppScope::class)
@Inject
class BolusInProgress(
    private val preferences: Preferences,
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) {

    private class Saved(
        /** Phone time just before the start went to the pump. The time and the pump id of the record made from it. */
        val start: Long,
        val insulin: Double,
        val type: BS.Type
    ) {

        /** By this time the pump has ended and logged the bolus, if it took it, also at its slowest speed. */
        val end: Long get() = start + (insulin * SLOWEST_SECONDS_PER_UNIT * 1000).toLong() + LOGGED_AFTER_END
    }

    fun save(start: Long, insulin: Double, type: BS.Type) =
        preferences.put(DanaStringNonKey.BolusInProgress, listOf(start, insulin, type.name).joinToString(SEPARATOR))

    fun clear() = preferences.remove(DanaStringNonKey.BolusInProgress)

    /**
     * Plain DanaR, after a status read. [lastBolusTime] and [lastBolusAmount] are the last bolus of the pump.
     *
     * The whole amount counts until the pump has surely ended the bolus. Then the amount the pump logged counts, when its
     * last bolus is this one. When it is not (a newer bolus given on the pump, or a start the pump never took), the
     * whole amount stays: that never counts less than was given.
     */
    suspend fun recordFromPump(lastBolusTime: Long?, lastBolusAmount: Double?, pumpType: PumpType, serial: String) {
        val saved = read() ?: return
        val ended = dateUtil.now() >= saved.end
        // The pump gives the time in whole minutes, so the bolus may show up to a minute before its start.
        val logged = ended && lastBolusTime != null && lastBolusTime >= saved.start - LAST_BOLUS_BEFORE_START && lastBolusTime <= saved.end
        val amount = if (logged) lastBolusAmount ?: saved.insulin else saved.insulin
        aapsLogger.debug(
            LTag.PUMP,
            "Bolus of a killed app, started ${dateUtil.dateAndTimeAndSecondsString(saved.start)}: $amount U of ${saved.insulin} U, " +
                when {
                    logged -> "as the pump logged it"
                    ended  -> "the pump does not show it"
                    else   -> "it may still run"
                }
        )
        record(saved, amount, pumpType, serial)
        if (ended) clear()
    }

    /** Korean DanaR, after a status read. Its status has no last bolus, and the pump goes on without the app, so the whole amount counts. */
    suspend fun recordWhole(pumpType: PumpType, serial: String) {
        val saved = read() ?: return
        aapsLogger.debug(LTag.PUMP, "Bolus of a killed app, started ${dateUtil.dateAndTimeAndSecondsString(saved.start)}: all ${saved.insulin} U, the pump cannot tell what it gave")
        record(saved, saved.insulin, pumpType, serial)
        clear()
    }

    /** The same pump id each time, so a second call corrects the record instead of adding one. */
    private suspend fun record(saved: Saved, amount: Double, pumpType: PumpType, serial: String) {
        pumpSync.syncBolusWithPumpId(saved.start, PumpInsulin(amount), saved.type, saved.start, pumpType, serial)
    }

    private fun read(): Saved? {
        val text = preferences.get(DanaStringNonKey.BolusInProgress)
        if (text.isEmpty()) return null
        val parts = text.split(SEPARATOR)
        val saved = parts.takeIf { it.size == 3 }?.let {
            val start = it[0].toLongOrNull()
            val insulin = it[1].toDoubleOrNull()
            val type = BS.Type.entries.firstOrNull { type -> type.name == it[2] }
            if (start != null && insulin != null && type != null) Saved(start, insulin, type) else null
        }
        if (saved == null) {
            aapsLogger.error(LTag.PUMP, "Saved bolus not readable, removed: $text")
            clear()
        }
        return saved
    }

    private companion object {

        const val SEPARATOR = ";"

        /** The slowest bolus speed a DanaR can be set to. */
        const val SLOWEST_SECONDS_PER_UNIT = 60

        /** Time for the pump to log the bolus after its end, and for a pump clock a little off. */
        const val LOGGED_AFTER_END = 60_000L

        /** The pump gives the time of its last bolus in whole minutes, and its clock may be some seconds off. */
        const val LAST_BOLUS_BEFORE_START = 70_000L
    }
}

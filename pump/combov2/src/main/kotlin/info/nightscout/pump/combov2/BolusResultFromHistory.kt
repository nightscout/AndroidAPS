package info.nightscout.pump.combov2

import info.nightscout.comboctl.main.Pump as ComboCtlPump

/** What the history read after a bolus says about it, when its own result did not get out (#5377). */
internal enum class BolusResultFromHistory { GIVEN, GIVEN_WITH_OTHER_BOLUSES, CANCELLED, STOPPED_EARLY, NOT_GIVEN }

/**
 * Decides what to tell about a bolus from the history read after it. Used when an alert screen,
 * a cancel or another error took the place of the bolus's own result on the way out of
 * `Pump.deliverBolus()`.
 *
 * @param givenAmount What the history shows this bolus gave. In 0.1 IU units.
 * @param requestedAmount What was asked for. In 0.1 IU units.
 */
internal fun bolusResultFromHistory(givenAmount: Int, requestedAmount: Int, outcome: ComboCtlPump.BolusOutcome.HistoryRead) = when {
    givenAmount >= requestedAmount ->
        if (outcome.otherBolusesFound) BolusResultFromHistory.GIVEN_WITH_OTHER_BOLUSES else BolusResultFromHistory.GIVEN

    outcome.cancelledByUser        -> BolusResultFromHistory.CANCELLED
    givenAmount > 0                -> BolusResultFromHistory.STOPPED_EARLY
    else                           -> BolusResultFromHistory.NOT_GIVEN
}

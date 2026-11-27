package app.aaps.workflow

import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Sends errors of the calculation to Crashlytics, each kind once for the life of this object.
 *
 * Before this, such an error went only to the AAPS log. When it ended the prepare phase, the loop did
 * not run for that glucose reading, and only the user's log knew about it (#5662).
 *
 * Once per kind, because a calculation runs for every glucose reading. An error that comes from stored
 * data comes back on every run while that data is in the window, which can be a whole day, and one
 * report is enough to find it. The caller still writes every error to the AAPS log.
 *
 * A kind is the message plus the type of the error, so a different error in the same place is still
 * sent. The message goes into the Crashlytics log just before the error, so the report says what the
 * error stopped.
 */
internal class CalculationErrorReports(private val fabricPrivacy: FabricPrivacy) {

    private val reported = MutableStateFlow(emptySet<String>())

    /**
     * Sends [error], unless an error of the same type was sent with the same [message] before.
     *
     * [message] names the place, for example the phase. It is also the key, so it must not hold values
     * that change from run to run, such as a time.
     */
    fun report(message: String, error: Throwable) {
        val kind = "$message: ${error::class.simpleName}"
        if (kind in reported.getAndUpdate { it + kind }) return
        fabricPrivacy.logMessage(message)
        fabricPrivacy.logException(error)
    }
}

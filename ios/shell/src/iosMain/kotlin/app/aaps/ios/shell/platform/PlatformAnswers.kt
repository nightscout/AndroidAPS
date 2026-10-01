package app.aaps.ios.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag

/**
 * Says that something is absent **by design**, at debug level.
 *
 * The difference from `notOnIosYet` in the `missing` package is the whole point of these two
 * packages existing separately:
 *
 * - `missing` holds work not done yet. It logs at **error**, because every call is a screen reaching
 *   for something that ought to be there, and a run should make that impossible to miss.
 * - `platform` holds answers about what an iOS client **is**. A glucose source that talks over
 *   Android broadcasts, or screen-usage statistics collected from an activity lifecycle, are not
 *   late - they are things this build does not have. Logging those at error would fill the log with
 *   correct behaviour and teach a reader to ignore the level that matters.
 *
 * Debug rather than silence, though: when a shared screen hides an option, being able to see why in
 * the log is worth one line.
 */
internal fun AAPSLogger.notOnThisPlatform(what: String) {
    debug(LTag.CORE, "Not available on an iOS client: $what")
}

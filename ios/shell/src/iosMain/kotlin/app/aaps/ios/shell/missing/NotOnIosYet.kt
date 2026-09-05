package app.aaps.ios.shell.missing

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag

/**
 * The first pass at running the real app on iOS: say what is missing, loudly, instead of pretending.
 *
 * Every class in this package satisfies a binding the Metro graph needs but iOS has no
 * implementation for yet. They exist so the app starts and can be looked at - the alternative is a
 * graph that will not build, and nothing on screen to discuss.
 *
 * Two rules keep this honest, and both matter more than they look:
 *
 * - **Nothing here returns a plausible value.** A stub that answers `false` or an empty list reads
 *   as a working feature that found nothing, and that is how a stub survives into a release. These
 *   either throw or return a value that is obviously a placeholder, and always log first.
 * - **Nothing here is silent.** Every call is recorded at error level with the class and method, so
 *   the log says exactly which missing piece a screen reached for.
 *
 * These are scaffolding for the first pass and are meant to be deleted one at a time, not extended.
 */
internal fun AAPSLogger.notOnIosYet(what: String) {
    error(LTag.CORE, "Not implemented on iOS yet: $what")
}

/**
 * For a call whose result the caller acts on, where inventing one would be a lie.
 *
 * Throwing is deliberate. A wrong answer to "is the master password set" or "decrypt this export"
 * would send a screen down a path it must not take, and a crash on a screen nobody has wired up yet
 * is cheaper to find than a silent wrong answer in something that looks finished.
 */
internal fun AAPSLogger.failNotOnIosYet(what: String): Nothing {
    error(LTag.CORE, "Not implemented on iOS yet: $what")
    throw NotImplementedError("$what is not implemented on iOS yet")
}

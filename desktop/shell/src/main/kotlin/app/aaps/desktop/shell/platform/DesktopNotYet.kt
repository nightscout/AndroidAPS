package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag

/**
 * For the bindings the desktop graph needs but has no answer for yet.
 *
 * The same two rules the Apple side settled on, and they matter more than they look:
 *
 * - **Nothing returns a plausible value.** A stub that answers `false` or an empty list reads as a
 *   working feature that found nothing, and that is how a stub survives into a release. These either
 *   throw or return something obviously unfinished, and they always log first.
 * - **Nothing is silent.** Every call is recorded with the class and method, so the log says exactly
 *   which missing piece a screen reached for.
 *
 * These are scaffolding, meant to be deleted one at a time rather than extended. Where desktop
 * genuinely *can* do the thing - opening a URL, listing exported files - it does it, and those
 * classes do not use this file.
 */
internal fun AAPSLogger.notOnDesktopYet(what: String) {
    error(LTag.CORE, "Not implemented on desktop yet: $what")
}

/**
 * For a call whose result the caller acts on, where inventing one would be a lie.
 *
 * Throwing is deliberate. A wrong answer to "did the export succeed" or "is this the right password"
 * would send a screen down a path it must not take, and a crash on a screen nobody has finished is
 * cheaper to find than a silent wrong answer in something that looks done.
 */
internal fun AAPSLogger.failNotOnDesktopYet(what: String): Nothing {
    error(LTag.CORE, "Not implemented on desktop yet: $what")
    throw NotImplementedError("$what is not implemented on desktop yet")
}

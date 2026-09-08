package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.configuration.AppExit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.system.exitProcess

/**
 * Ends the desktop app.
 *
 * Exiting is the easy half and it is honoured exactly. Restarting is not: Android hands its launch
 * intent to `AlarmManager` and lets the alarm start it again, and a JVM has no equivalent - a
 * relaunch would mean spawning a new process from a command line that differs between a packaged
 * `AAPS.exe`, a `gradle run`, and a jpackage install. Guessing that wrongly ends with the app closed
 * and nothing coming back.
 *
 * So a restart closes the app and says so in the log. The user starts it again, which on a desktop is
 * one click - the thing Android goes to that trouble to avoid is a phone user hunting for an app that
 * vanished, and that is not the situation here.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DesktopAppExit @Inject constructor(
    private val aapsLogger: AAPSLogger
) : AppExit {

    override fun exit(launchAgain: Boolean) {
        if (launchAgain) aapsLogger.info(LTag.CORE, "Restart asked for; desktop can only close - start AAPS again to finish it")
        exitProcess(0)
    }
}

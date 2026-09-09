package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.awt.Desktop
import java.net.URI
import java.util.Locale

/**
 * The Google sign in page, in the user's own browser.
 *
 * [AuthBrowser] argues for a page presented *over* the app, and on a phone that is the only thing
 * that works: iOS and Android suspend a backgrounded app within seconds, which takes the loopback
 * listener down with it and leaves the redirect arriving at a closed port.
 *
 * That reasoning is a mobile one and does not carry to a desktop. A JVM behind another window keeps
 * running, `JvmAuthRedirectListener` keeps accepting, and the redirect is caught whether AAPS is in
 * front or not. So the system browser is not merely allowed here, it is the better answer: the user
 * signs in somewhere they can see the address bar, with the Google session, password manager and
 * two factor device they already have set up.
 *
 * ## Why there are two ways to open it
 *
 * `Desktop.browse` is the portable one and is what [app.aaps.core.interfaces.ui.UrlOpener] uses for
 * ordinary links. It is missing more often than its name suggests - a Linux session with no
 * `java.awt.Desktop` support, which includes running the desktop client under WSLg - and a failed
 * link is a shrug while a failed sign in is a feature the user cannot reach at all. So a platform
 * launcher is tried after it.
 *
 * The address is deliberately never logged. It carries the OAuth `state`, which is what stops a
 * forged redirect being accepted, and AAPS logs get exported and posted to support threads.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DesktopAuthBrowser @Inject constructor(private val aapsLogger: AAPSLogger) : AuthBrowser {

    override fun show(url: String): Boolean {
        if (browseWithAwt(url) || browseWithPlatformCommand(url)) {
            aapsLogger.debug(LTag.CORE, "$TAG opened the sign in page")
            return true
        }
        aapsLogger.error(LTag.CORE, "$TAG no browser could be opened for the sign in")
        return false
    }

    /**
     * Closing it is not ours to do.
     *
     * The page is a tab in the user's own browser rather than a window this app owns, so there is
     * nothing to take away - and taking away someone's tab would be wrong even if the JVM could. The
     * sign in ends on a Google page saying so, which is a reasonable thing to leave on screen.
     *
     * Empty on purpose, not unfinished.
     */
    override fun dismiss() = Unit

    private fun browseWithAwt(url: String): Boolean = runCatching {
        if (!Desktop.isDesktopSupported()) return@runCatching false
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) return@runCatching false
        desktop.browse(URI(url))
        true
    }.getOrDefault(false)

    /**
     * The desktop environment's own "open this" command.
     *
     * Started with an argument list rather than a command line, so the address is one argument and
     * cannot be read as shell syntax. Only whether the launcher started is checked: these commands
     * hand off to a browser and return immediately, so waiting for an exit code would mean waiting
     * for the user to finish signing in.
     */
    private fun browseWithPlatformCommand(url: String): Boolean {
        val command = browserCommandFor(System.getProperty("os.name").orEmpty(), url)
        return runCatching { ProcessBuilder(command).start(); true }.getOrDefault(false)
    }

    internal companion object {

        private const val TAG = "DesktopAuthBrowser:"

        /**
         * The launcher for [osName], with [url] as its own argument.
         *
         * Separate from the starting so it can be checked without opening a browser on whatever
         * machine the tests run on. Linux is the fallback rather than a case of its own: `xdg-open`
         * is the freedesktop standard, so every desktop Unix that is not macOS is better served by
         * trying it than by being refused for having an unrecognised name.
         */
        internal fun browserCommandFor(osName: String, url: String): List<String> {
            val os = osName.lowercase(Locale.ROOT)
            return when {
                os.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
                os.contains("mac") -> listOf("open", url)
                else               -> listOf("xdg-open", url)
            }
        }
    }
}

package app.aaps.implementation.maintenance.cloud

import app.aaps.implementation.maintenance.cloud.DesktopAuthBrowser.Companion.browserCommandFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which launcher the desktop reaches for, and how the address is passed to it.
 *
 * Only the choosing is checked. Actually starting a browser would open one on whoever runs the
 * tests, and `Desktop.browse` answers differently on a build machine than on a real desktop, so the
 * part worth pinning is the mapping - which is also the part that can quietly be wrong on a
 * platform nobody develops on.
 */
class DesktopAuthBrowserTest {

    @Test
    fun `windows opens through the shell handler`() {
        assertEquals(
            listOf("rundll32", "url.dll,FileProtocolHandler", "https://accounts.google.com/o/oauth2/v2/auth"),
            browserCommandFor("Windows 11", "https://accounts.google.com/o/oauth2/v2/auth")
        )
    }

    @Test
    fun `macos opens with open`() {
        assertEquals(listOf("open", "https://x"), browserCommandFor("Mac OS X", "https://x"))
    }

    @Test
    fun `linux opens with xdg-open`() {
        assertEquals(listOf("xdg-open", "https://x"), browserCommandFor("Linux", "https://x"))
    }

    /**
     * An unknown Unix gets `xdg-open` rather than a refusal.
     *
     * The desktop client is run under WSLg and on BSDs whose `os.name` nobody enumerated, and being
     * turned away for having an unfamiliar name would be worse than trying the standard command.
     */
    @Test
    fun `an unfamiliar system still gets a try`() {
        assertEquals(listOf("xdg-open", "https://x"), browserCommandFor("FreeBSD", "https://x"))
    }

    @Test
    fun `the name is read whatever its case`() {
        assertEquals(listOf("open", "https://x"), browserCommandFor("MAC OS X", "https://x"))
        assertEquals(listOf("rundll32", "url.dll,FileProtocolHandler", "https://x"), browserCommandFor("WINDOWS 10", "https://x"))
    }

    /**
     * The address stays one argument.
     *
     * It is started with an argument list rather than a command line so that nothing in a URL can be
     * read as shell syntax. A URL full of `&` between query parameters is exactly the shape that
     * would break, or run something, if this were ever flattened into a string.
     */
    @Test
    fun `the address is a single argument, however many separators it holds`() {
        val url = "https://accounts.google.com/o/oauth2/v2/auth?client_id=a&state=b&code_challenge=c"

        val command = browserCommandFor("Linux", url)

        assertEquals(2, command.size)
        assertEquals(url, command.last())
        assertTrue(command.none { it != url && it.contains("&") }, "no separator should have leaked into the launcher arguments")
    }
}

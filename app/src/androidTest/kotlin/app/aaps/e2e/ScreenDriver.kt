package app.aaps.e2e

import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import java.util.regex.Pattern

/**
 * Taps through the app's Compose screens with uiautomator, for the UI tests of the pump drivers.
 *
 * Selectors match case-insensitively against text OR content description. A step waits for a **unique** text of the
 * next screen, because labels like "Next" are on many screens. A single tap is routinely dropped while Compose
 * recomposes, so [openVia] taps again until the next screen shows; use it only for idempotent moves. A button that
 * starts a pump command is tapped once with [click], and the result is awaited with one of the waits. English only.
 *
 * A text to tap that is not on the screen is looked for by scrolling: on a small screen, as the 320 x 640 one of the CI
 * emulators, Compose does not lay out what is below the visible part, so uiautomator cannot find it there.
 */
class ScreenDriver(private val device: UiDevice) {

    private val packageName get() = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    private fun byText(s: String): BySelector = By.text(Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))
    private fun byDesc(s: String): BySelector = By.desc(Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))
    private fun byTextContains(s: String): BySelector =
        By.text(Pattern.compile(".*" + Pattern.quote(s) + ".*", Pattern.CASE_INSENSITIVE or Pattern.DOTALL))

    /** The first object whose text or description is, or else contains, [label]. */
    fun find(label: String, timeout: Long = STEP_TIMEOUT): UiObject2 {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            device.findObject(byText(label))?.let { return it }
            device.findObject(byDesc(label))?.let { return it }
            device.findObject(byTextContains(label))?.let { return it }
            scrollOn()
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out after ${timeout}ms looking for '$label'")
    }

    private var scrollDown = true

    /** One scroll step of the app's scrollable view: down to its end, then up to its start, and so on. */
    private fun scrollOn() {
        val scrollable = device.findObject(By.pkg(packageName).scrollable(true)) ?: return
        runCatching {
            // scroll() says whether the view can still scroll that way.
            if (!scrollable.scroll(if (scrollDown) Direction.DOWN else Direction.UP, SCROLL_PERCENT)) scrollDown = !scrollDown
        }
    }

    /** One tap. For a button that starts a command: a second tap would start it again. */
    fun click(label: String) = withStaleRetry { find(label).click() }

    /**
     * One tap on the button that shows exactly [label]. For a label that is also a title or part of a sentence, as
     * Carelevo's "Safety Check", where [click] could hit the title. A Compose button merges its text into its own node.
     */
    fun clickButton(label: String, timeout: Long = STEP_TIMEOUT) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            val button = device.findObject(byText(label).clickable(true)) ?: device.findObject(By.clickable(true).hasDescendant(byText(label)))
            if (button != null) {
                try {
                    button.click()
                    return
                } catch (_: StaleObjectException) {
                    device.waitForIdle(STALE_SETTLE_MS)
                    continue
                }
            }
            scrollOn()
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out after ${timeout}ms looking for a button '$label'")
    }

    /**
     * Taps [open] until [expect] shows. Only for moves that do no harm when they happen twice.
     * @param exact [expect] is a whole text, not part of one: "Pair" must not match "Unpair device"
     */
    fun openVia(open: String, expect: String, attempts: Int = 4, exact: Boolean = false) {
        repeat(attempts) {
            click(open)
            if (if (exact) waitForExact(expect) else waitForVisible(expect)) return
        }
        error("'$expect' not visible after $attempts taps on '$open'")
    }

    fun waitForVisible(label: String, timeout: Long = STEP_TIMEOUT): Boolean {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (device.findObject(byText(label)) != null || device.findObject(byDesc(label)) != null ||
                device.findObject(byTextContains(label)) != null
            ) return true
            device.waitForIdle(IDLE_MS)
        }
        return false
    }

    fun waitForVisibleOrThrow(label: String, timeout: Long = STEP_TIMEOUT) {
        if (!waitForVisible(label, timeout)) error("Timed out after ${timeout}ms looking for '$label'")
    }

    /** A text that is exactly [label]: "Pair" must not match "Unpair device", nor "Suspend" match "Suspended". */
    fun waitForExact(label: String, timeout: Long = STEP_TIMEOUT): Boolean {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (device.findObject(byText(label)) != null) return true
            device.waitForIdle(IDLE_MS)
        }
        return false
    }

    fun waitForExactOrThrow(label: String, timeout: Long = STEP_TIMEOUT) {
        if (!waitForExact(label, timeout)) error("Timed out after ${timeout}ms looking for exactly '$label'")
    }

    fun assertVisibleContains(substring: String, timeout: Long = STEP_TIMEOUT) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (device.findObject(byTextContains(substring)) != null) return
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out after ${timeout}ms looking for text containing '$substring'")
    }

    /** The main screen, after a sheet that opened at the start is closed. */
    fun waitForOverview(timeout: Long = INIT_TIMEOUT) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            // Back, not a scrim tap: the tap goes to the scrim node centre, which is ON the sheet once it is
            // tall enough (see AbstractDanaEmulatorUiTest). The find guards it, so Back needs a sheet to be up.
            runCatching { if (device.findObject(byDesc("Close sheet")) != null) device.pressBack() }
            if (device.findObject(byDesc("Open navigation")) != null) return
            device.waitForIdle(IDLE_MS)
        }
        error("Overview never appeared within ${timeout}ms")
    }

    fun pressBack() {
        device.pressBack()
    }

    /** Types [text] into the text field on the screen, as the DanaR password. For a screen with one text field. */
    fun typeIntoTheField(text: String, timeout: Long = STEP_TIMEOUT) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            val field = device.findObject(By.pkg(packageName).clazz(EDIT_TEXT))
            if (field != null) {
                try {
                    field.text = text
                    return
                } catch (_: StaleObjectException) {
                    device.waitForIdle(STALE_SETTLE_MS)
                    continue
                }
            }
            scrollOn()
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out after ${timeout}ms looking for a text field")
    }

    /** Every text and description on the screen, to the log under [tag] and returned: what a failed step was looking at. */
    fun logScreen(tag: String): String =
        runCatching {
            val items = device.findObjects(By.pkg(packageName)).mapNotNull { o ->
                val txt = runCatching { o.text }.getOrNull()?.takeIf { it.isNotBlank() }
                val desc = runCatching { o.contentDescription }.getOrNull()?.takeIf { it.isNotBlank() }
                if (txt != null || desc != null) "[t=$txt|d=$desc]" else null
            }
            items.joinToString(" ").also { all -> all.chunked(LOG_CHUNK).forEachIndexed { i, c -> Log.e(tag, "$i $c") } }
        }.getOrDefault("")

    private inline fun withStaleRetry(times: Int = STALE_RETRIES, block: () -> Unit) {
        var last: StaleObjectException? = null
        repeat(times) {
            try {
                block(); return
            } catch (e: StaleObjectException) {
                last = e; device.waitForIdle(STALE_SETTLE_MS)
            }
        }
        throw last ?: IllegalStateException("withStaleRetry exhausted")
    }

    companion object {

        const val STEP_TIMEOUT = 30_000L

        /** The test launches the full main activity, so first paint can lag under CI load. */
        const val INIT_TIMEOUT = 120_000L

        /** A move that runs pump commands. */
        const val COMMAND_TIMEOUT = 60_000L

        private const val IDLE_MS = 300L
        private const val STALE_RETRIES = 10
        private const val STALE_SETTLE_MS = 700L
        private const val LOG_CHUNK = 3500
        private const val SCROLL_PERCENT = 0.5f
        private const val EDIT_TEXT = "android.widget.EditText"
    }
}

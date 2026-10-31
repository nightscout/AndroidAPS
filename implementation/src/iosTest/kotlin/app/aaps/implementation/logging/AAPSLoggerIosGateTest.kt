package app.aaps.implementation.logging

import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.LogElement
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which log lines this logger keeps, and on whose authority.
 *
 * It used to answer both questions wrongly. The switch it read was `LTag.defaultValue`, a
 * compile-time constant rather than a setting, so the eight tags that ship `false` could never be
 * turned on however the log-settings sheet was set - "switch on NSCLIENT_SYNC and send me the log"
 * was impossible on iOS and desktop. And it applied that switch to **errors** as well, which Android
 * never does, so an error raised on one of those tags was dropped with nothing recording that it had
 * been. Both together meant the platform least able to be debugged in person was also the one that
 * threw away the evidence.
 */
class AAPSLoggerIosGateTest {

    private class FakeElement(override var name: String, override var enabled: Boolean) : LogElement {

        override var defaultValue: Boolean = false
        override fun enable(enabled: Boolean) {
            this.enabled = enabled
        }

        override fun resetToDefault() {}
    }

    private class FakeL(private val enabledTags: Set<String>) : L {

        override fun resetToDefaults() {}
        override fun findByName(name: String): LogElement = FakeElement(name, name in enabledTags)
        override fun logElements(): List<LogElement> = emptyList()
    }

    private fun loggerWith(vararg enabledTags: String) =
        AAPSLoggerIos(fileName = "unit-test-should-not-write.log", logConfig = { FakeL(enabledTags.toSet()) })

    /** A tag that ships disabled must still be switchable on, which is the whole point of the sheet. */
    @Test
    fun `a tag switched on in settings is enabled even when it ships disabled`() {
        val offByDefault = LTag.entries.first { !it.defaultValue }

        assertTrue(loggerWith(offByDefault.tag).enabled(offByDefault))
    }

    /** ...and one switched off is off, even though it ships enabled. */
    @Test
    fun `a tag switched off in settings is disabled even when it ships enabled`() {
        val onByDefault = LTag.entries.first { it.defaultValue }

        assertFalse(loggerWith().enabled(onByDefault))
    }

    /**
     * Before the graph can supply the settings there is no answer but the shipped default. The probe
     * shell and these tests build the logger with no graph at all, so this is a real path, not a
     * theoretical one.
     */
    @Test
    fun `with no settings available the shipped default is used`() {
        val logger = AAPSLoggerIos(fileName = "unit-test-should-not-write.log")

        assertTrue(logger.enabled(LTag.entries.first { it.defaultValue }))
        assertFalse(logger.enabled(LTag.entries.first { !it.defaultValue }))
    }

    /** A settings lookup that throws must not silence logging; fall back rather than propagate. */
    @Test
    fun `a failing settings lookup falls back to the shipped default`() {
        val throwing = AAPSLoggerIos(
            fileName = "unit-test-should-not-write.log",
            logConfig = { throw IllegalStateException("preferences not ready") }
        )

        assertTrue(throwing.enabled(LTag.entries.first { it.defaultValue }))
    }
}

package app.aaps.implementation.logging

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The slf4j style `{}` filling the iOS logger does by hand.
 *
 * Callers all over AAPS write `aapsLogger.debug(tag, "value is {}", x)`, because that is what the
 * Android logger accepts through logback. This reimplements it, so the two have to agree - a
 * mismatch would garble log lines exactly when someone is reading them to work out what went wrong.
 *
 * The behaviour on a wrong number of arguments matters as much as the normal case: logback drops
 * extras and leaves missing ones as `{}` rather than throwing, and a logger that throws would take
 * down whatever was trying to report a problem.
 */
class AAPSLoggerIosTest {

    private val logger = AAPSLoggerIos(fileName = "unit-test-should-not-write.log")

    private fun fill(format: String, vararg args: Any?): String = with(logger) { format.fill(args) }

    @Test
    fun `one placeholder takes one argument`() {
        assertEquals("value is 42", fill("value is {}", 42))
    }

    @Test
    fun `placeholders are filled in order`() {
        assertEquals("from 1 to 2", fill("from {} to {}", 1, 2))
    }

    @Test
    fun `a message with no placeholders is unchanged`() {
        assertEquals("nothing to fill", fill("nothing to fill", 1, 2))
    }

    /** logback drops the extras rather than throwing, and so must this. */
    @Test
    fun `extra arguments are dropped`() {
        assertEquals("only 1", fill("only {}", 1, 2, 3))
    }

    /** A missing argument leaves the placeholder visible, which is logback's behaviour too. */
    @Test
    fun `a missing argument leaves the placeholder`() {
        assertEquals("first 1 second {}", fill("first {} second {}", 1))
    }

    @Test
    fun `no arguments leaves every placeholder`() {
        assertEquals("{} and {}", fill("{} and {}"))
    }

    @Test
    fun `null is written out rather than skipped`() {
        assertEquals("value is null", fill("value is {}", null))
    }

    /**
     * A value that itself contains `{}` must not be re-filled.
     *
     * Filling left to right and advancing past what was written is what stops an argument's own
     * braces from swallowing the next argument.
     */
    @Test
    fun `an argument containing braces does not consume the next one`() {
        assertEquals("a {} b 2", fill("a {} b {}", "{}", 2))
    }
}

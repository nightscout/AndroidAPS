package app.aaps.core.ui.compose

import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A duration is shown exact to the minute. The Careportal list used to cut it to the largest whole
 * unit, so 60 and 119 minutes both showed as "1 hour" (#5517).
 */
class FormatMinutesAsDurationTest {

    /** Answers which string was asked for and with which arguments, so the test does not depend on the translation. */
    private val rh = object : TextResolver {
        override fun gs(ref: TextRef): String = describe(ref, emptyList())
        override fun gs(ref: TextRef, vararg args: Any?): String = describe(ref, args.toList())
        override fun gsNotLocalised(ref: TextRef): String = describe(ref, emptyList())
    }

    private fun describe(ref: TextRef, args: List<Any?>): String = when (ref) {
        CoreUiStrings.format_mins         -> "mins$args"
        CoreUiStrings.format_hours_only   -> "hours$args"
        CoreUiStrings.format_hour_minute  -> "hour_minute$args"
        else                              -> "other$args"
    }

    @Test
    fun underAnHourIsMinutes() {
        assertEquals("mins[45]", formatMinutesAsDuration(45, rh))
    }

    @Test
    fun aWholeHourHasNoMinutes() {
        assertEquals("hours[1]", formatMinutesAsDuration(60, rh))
    }

    @Test
    fun theMinutesAfterTheHourAreKept() {
        assertEquals("hour_minute[1, 59]", formatMinutesAsDuration(119, rh))
    }

    @Test
    fun daysAreShownAsHours() {
        assertEquals("hours[72]", formatMinutesAsDuration(3 * 24 * 60, rh))
    }
}

package app.aaps.plugins.source.compose

import app.aaps.core.interfaces.InterfacesStringsValues
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.resources.formatTemplate
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The sensor age keeps the two largest units, and is built only from translated templates.
 *
 * The resolver reads the real English table of `:core:interfaces`, so a wrong template or a wrong
 * argument order shows up here as wrong words.
 */
class SensorAgeFormatTest {

    private object EnglishText : TextResolver {

        override fun gs(ref: TextRef): String = when (ref) {
            is TextRef.Literal    -> ref.text
            is TextRef.Named      -> {
                val template = InterfacesStringsValues.textOf(ref.name, null) ?: ref.name
                if (ref.args.isEmpty()) template else formatTemplate(template, ref.args)
            }

            is TextRef.AndroidRes -> "res:${ref.id}"
        }

        override fun gs(ref: TextRef, vararg args: Any?): String = gs(ref.withArgs(*args))
        override fun gsNotLocalised(ref: TextRef): String = gs(ref)
    }

    @Test
    fun daysAndHours() {
        val age = (2.days + 5.hours + 40.minutes).inWholeMilliseconds
        assertEquals("2 days, 5 hours", formatSensorAge(age, EnglishText))
    }

    @Test
    fun hoursAndMinutes() {
        val age = (3.hours + 10.minutes + 59.seconds).inWholeMilliseconds
        assertEquals("3 hours, 10 minutes", formatSensorAge(age, EnglishText))
    }

    @Test
    fun onlyMinutes() {
        assertEquals("7 minutes", formatSensorAge(7.minutes.inWholeMilliseconds, EnglishText))
    }

    @Test
    fun oneOfEachUsesTheSingular() {
        val age = (1.days + 1.hours).inWholeMilliseconds
        assertEquals("1 day, 1 hour", formatSensorAge(age, EnglishText))
        assertEquals("1 minute", formatSensorAge(1.minutes.inWholeMilliseconds, EnglishText))
    }

    @Test
    fun fullDaysShowZeroHours() {
        assertEquals("10 days, 0 hours", formatSensorAge(10.days.inWholeMilliseconds, EnglishText))
    }

    @Test
    fun negativeAgeIsZeroMinutes() {
        assertEquals("0 minutes", formatSensorAge(-5_000L, EnglishText))
    }
}

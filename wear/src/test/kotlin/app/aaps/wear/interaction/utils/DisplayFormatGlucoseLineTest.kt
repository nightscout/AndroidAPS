package app.aaps.wear.interaction.utils

import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import app.aaps.wear.WearTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

/**
 * Tests for [DisplayFormat.longGlucoseLine] — the LONG_TEXT glucose complication line.
 *
 * [DisplayFormatTest] already covers [DisplayFormat.shortTimeSince], [DisplayFormat.longDetailsLine],
 * [DisplayFormat.detailedIob] and [DisplayFormat.detailedCob]; this file focuses solely on the
 * previously untested [DisplayFormat.longGlucoseLine], exercising:
 *  - simple vs. detailed delta selection via key_show_detailed_delta
 *  - unicode Δ prefix vs. empty prefix via complication_unicode
 *  - delta precision reduction through SmallestDoubleString.minimise(8)
 *  - full composition with a fresh timestamp hitting the "0'" age bucket
 */
class DisplayFormatGlucoseLineTest : WearTestBase() {

    private lateinit var displayFormat: DisplayFormat

    // Complication glyphs referenced via escapes so expectations don't depend on source encoding
    private val delta = "Δ"     // Δ delta symbol prefix
    private val arrow = "↗"     // ↗ slope arrow

    @BeforeEach
    fun mock() {
        displayFormat = DisplayFormat()
        displayFormat.sp = sp
        displayFormat.context = context
        whenever(sp.getBoolean("complication_unicode", true)).thenReturn(true)
        whenever(sp.getBoolean(R.string.key_show_detailed_delta, false)).thenReturn(false)
        whenever(context.getString(R.string.hour_short)).thenReturn("h")
        whenever(context.getString(R.string.day_short)).thenReturn("d")
        whenever(context.getString(R.string.week_short)).thenReturn("w")
    }

    private fun singleBg(
        sgvString: String = "120",
        slopeArrow: String = arrow,
        delta: String = "+2.5",
        deltaDetailed: String = "+2.53",
        timeStamp: Long = System.currentTimeMillis()
    ): Array<EventData.SingleBg> =
        arrayOf(
            EventData.SingleBg(
                dataset = 0,
                timeStamp = timeStamp,
                sgvString = sgvString,
                glucoseUnits = "mg/dl",
                slopeArrow = slopeArrow,
                delta = delta,
                deltaDetailed = deltaDetailed,
                avgDelta = "--",
                avgDeltaDetailed = "--",
                sgvLevel = 0,
                sgv = 120.0,
                high = 180.0,
                low = 70.0
            )
        )

    @Test fun composesFullLineWithFreshTimestamp() {
        // 120 + ↗ + " " + Δ + "+2.5" + " (" + "0'" + ")"
        assertThat(displayFormat.longGlucoseLine(singleBg(), 0)).isEqualTo("120$arrow ${delta}+2.5 (0')")
    }

    @Test fun usesSimpleDeltaWhenDetailedPreferenceOff() {
        whenever(sp.getBoolean(R.string.key_show_detailed_delta, false)).thenReturn(false)
        assertThat(displayFormat.longGlucoseLine(singleBg(delta = "+2.5", deltaDetailed = "+2.53"), 0))
            .isEqualTo("120$arrow ${delta}+2.5 (0')")
    }

    @Test fun usesDetailedDeltaWhenDetailedPreferenceOn() {
        whenever(sp.getBoolean(R.string.key_show_detailed_delta, false)).thenReturn(true)
        assertThat(displayFormat.longGlucoseLine(singleBg(delta = "+2.5", deltaDetailed = "+2.53"), 0))
            .isEqualTo("120$arrow ${delta}+2.53 (0')")
    }

    @Test fun omitsDeltaSymbolWhenUnicodeDisabled() {
        whenever(sp.getBoolean("complication_unicode", true)).thenReturn(false)
        // deltaSymbol() collapses to "" — no Δ prefix
        assertThat(displayFormat.longGlucoseLine(singleBg(), 0)).isEqualTo("120$arrow +2.5 (0')")
    }

    @Test fun keepsShortDeltaUnchangedWithinBudget() {
        // "+2.5" (len 4) fits within minimise(8) unchanged; leading '+' kept because whole line fits
        assertThat(displayFormat.longGlucoseLine(singleBg(delta = "+2.5"), 0))
            .isEqualTo("120$arrow ${delta}+2.5 (0')")
    }

    @Test fun reducesDeltaPrecisionToEightChars() {
        // "-12.34567" (len 9) > 8: sign kept ('-'), no trailing zeros to trim, fractional rounded
        // HALF_UP to remainingForFraction=4 digits -> "-12.3457"
        whenever(sp.getBoolean(R.string.key_show_detailed_delta, false)).thenReturn(true)
        assertThat(displayFormat.longGlucoseLine(singleBg(deltaDetailed = "-12.34567"), 0))
            .isEqualTo("120$arrow ${delta}-12.3457 (0')")
    }

    @Test fun dropsPlusSignWhenLineNeedsSpace() {
        // "+12.34567" (len 9) > 8: '+' removed first, bringing it to 8 chars -> "12.34567"
        whenever(sp.getBoolean(R.string.key_show_detailed_delta, false)).thenReturn(true)
        assertThat(displayFormat.longGlucoseLine(singleBg(deltaDetailed = "+12.34567"), 0))
            .isEqualTo("120$arrow ${delta}12.34567 (0')")
    }

    @Test fun rendersOlderReadingAgeBucket() {
        // timestamp 10 minutes ago -> "10'" bucket (well inside the minutes bucket)
        val tenMinAgo = System.currentTimeMillis() - 10 * Constants.MINUTE_IN_MS
        assertThat(displayFormat.longGlucoseLine(singleBg(timeStamp = tenMinAgo), 0))
            .isEqualTo("120$arrow ${delta}+2.5 (10')")
    }
}

package app.aaps.wear.interaction.utils

import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import app.aaps.wear.WearTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

/**
 * Tests for [DisplayFormat] — the complication text formatter. Exercises [DisplayFormat] directly
 * and, through carefully chosen inputs, [SmallestDoubleString] precision reduction.
 *
 * Restored and adapted (RawDisplayData -> EventData arrays, no injected WearUtil) from the test
 * suite that was dropped during the V4 AndroidX migration.
 */
class DisplayFormatTest : WearTestBase() {

    private lateinit var displayFormat: DisplayFormat

    // Complication glyphs, referenced via escapes so expectations don't depend on source encoding
    private val sep = "⁞"            // vertical separator
    private val basal = "⎍ "     // basal-rate symbol + thin space

    @BeforeEach
    fun mock() {
        displayFormat = DisplayFormat()
        displayFormat.sp = sp
        displayFormat.context = context
        whenever(sp.getBoolean("complication_unicode", true)).thenReturn(true)
        whenever(context.getString(R.string.hour_short)).thenReturn("h")
        whenever(context.getString(R.string.day_short)).thenReturn("d")
        whenever(context.getString(R.string.week_short)).thenReturn("w")
    }

    private fun status(cob: String = "0g", iobSum: String = "0U", currentBasal: String = "0U/h", iobDetail: String = ""): Array<EventData.Status> =
        arrayOf(
            EventData.Status(
                dataset = 0, externalStatus = "", iobSum = iobSum, iobDetail = iobDetail, cob = cob, currentBasal = currentBasal,
                battery = "", rigBattery = "", openApsStatus = -1L, bgi = "", batteryLevel = 1, tempTarget = "", tempTargetLevel = 0,
                reservoirString = "", reservoir = 0.0, reservoirLevel = 0
            )
        )

    @Test fun shortTimeSinceFormatsElapsedTime() {
        val now = System.currentTimeMillis()
        // offsets kept well inside each bucket to avoid boundary flakiness from the real clock
        assertThat(displayFormat.shortTimeSince(now)).isEqualTo("0'")
        assertThat(displayFormat.shortTimeSince(now - 30 * Constants.SECOND_IN_MS)).isEqualTo("0'")
        assertThat(displayFormat.shortTimeSince(now - 90 * Constants.SECOND_IN_MS)).isEqualTo("1'")
        assertThat(displayFormat.shortTimeSince(now - 10 * Constants.MINUTE_IN_MS)).isEqualTo("10'")
        assertThat(displayFormat.shortTimeSince(now - 59 * Constants.MINUTE_IN_MS)).isEqualTo("59'")
        assertThat(displayFormat.shortTimeSince(now - 2 * Constants.HOUR_IN_MS)).isEqualTo("2h")
        assertThat(displayFormat.shortTimeSince(now - 23 * Constants.HOUR_IN_MS)).isEqualTo("23h")
        assertThat(displayFormat.shortTimeSince(now - 2 * Constants.DAY_IN_MS)).isEqualTo("2d")
        assertThat(displayFormat.shortTimeSince(now - 6 * Constants.DAY_IN_MS)).isEqualTo("6d")
        assertThat(displayFormat.shortTimeSince(now - 8 * Constants.DAY_IN_MS)).isEqualTo("1w")
        assertThat(displayFormat.shortTimeSince(now - 21 * Constants.DAY_IN_MS)).isEqualTo("3w")
    }

    @Test fun longDetailsLineUnicode() {
        // fits: wide separators and basal symbol
        assertThat(displayFormat.longDetailsLine(status("0g", "0U", "3.5U/h"), 0)).isEqualTo("0g  $sep  0U  $sep  ${basal}3.5U/h")
        assertThat(displayFormat.longDetailsLine(status("50g", "7.56U", "0%"), 0)).isEqualTo("50g  $sep  7.56U  $sep  ${basal}0%")
        // too long: narrow separators, no basal symbol
        assertThat(displayFormat.longDetailsLine(status("12g", "3.23U", "120%"), 0)).isEqualTo("12g $sep 3.23U $sep 120%")
        assertThat(displayFormat.longDetailsLine(status("47g", "13.87U", "220%"), 0)).isEqualTo("47g $sep 13.87U $sep 220%")
        // IOB precision reduced to fit
        assertThat(displayFormat.longDetailsLine(status("2(40)g", "-1.5U", "0.55U/h"), 0)).isEqualTo("2(40)g $sep -2U $sep 0.55U/h")
        // COB precision reduced (extra dropped) to fit
        assertThat(displayFormat.longDetailsLine(status("19(38)g", "35.545U", "12.9U/h"), 0)).isEqualTo("19g $sep 36U $sep 12.9U/h")
        // still too long: separators collapse to single spaces
        assertThat(displayFormat.longDetailsLine(status("100(1)g", "12.345U", "6.98647U/h"), 0)).isEqualTo("100g 12U 6.98647U/h")
    }

    @Test fun longDetailsLineAscii() {
        whenever(sp.getBoolean("complication_unicode", true)).thenReturn(false)
        assertThat(displayFormat.longDetailsLine(status("0g", "0U", "3.5U/h"), 0)).isEqualTo("0g  |  0U  |  3.5U/h")
        assertThat(displayFormat.longDetailsLine(status("12g", "3.23U", "120%"), 0)).isEqualTo("12g  |  3.23U  |  120%")
        assertThat(displayFormat.longDetailsLine(status("19(38)g", "35.545U", "12.9U/h"), 0)).isEqualTo("19g | 36U | 12.9U/h")
    }

    @Test fun detailedIobSplitsBolusAndBasal() {
        assertThat(displayFormat.detailedIob(status(iobSum = "-1.29U", iobDetail = "(0,910|-2,20)"), 0)).isEqualTo(Pair.create("-1.29U", ",91 -2"))
        assertThat(displayFormat.detailedIob(status(iobSum = "3.50U", iobDetail = ""), 0)).isEqualTo(Pair.create("3.50U", ""))
        assertThat(displayFormat.detailedIob(status(iobSum = "12.5U", iobDetail = "(+1,4|-4.78)"), 0)).isEqualTo(Pair.create("12.5U", "1,4 -5"))
        assertThat(displayFormat.detailedIob(status(iobSum = "0.67U", iobDetail = "some junks"), 0)).isEqualTo(Pair.create(".67U", ""))
        assertThat(displayFormat.detailedIob(status(iobSum = "-11.0U", iobDetail = "(broken|data)"), 0)).isEqualTo(Pair.create("-11U", "-- --"))
        assertThat(displayFormat.detailedIob(status(iobSum = "-8.1U", iobDetail = "(|-8,1)"), 0)).isEqualTo(Pair.create("-8.1U", "-- -8"))
    }

    @Test fun detailedCobSplitsCurrentAndFuture() {
        assertThat(displayFormat.detailedCob(status(cob = "0g"), 0)).isEqualTo(Pair.create("0g", ""))
        assertThat(displayFormat.detailedCob(status(cob = "50g"), 0)).isEqualTo(Pair.create("50g", ""))
        assertThat(displayFormat.detailedCob(status(cob = "2(40)g"), 0)).isEqualTo(Pair.create("2g", "40g"))
        assertThat(displayFormat.detailedCob(status(cob = "13(5)g"), 0)).isEqualTo(Pair.create("13g", "5g"))
        assertThat(displayFormat.detailedCob(status(cob = "100(1)g"), 0)).isEqualTo(Pair.create("100g", "1g"))
    }
}

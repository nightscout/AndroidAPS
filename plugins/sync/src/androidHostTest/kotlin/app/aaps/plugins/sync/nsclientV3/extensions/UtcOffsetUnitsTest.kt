package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.CA
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.nssdk.localmodel.treatment.NSCarbs
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * `utcOffset` is carried in **minutes by Nightscout** and in **milliseconds by AAPS**.
 *
 * Every converter in this package does the conversion:
 * ```
 * inbound  NS -> AAPS : utcOffset = T.mins(utcOffset ?: 0L).msecs()
 * outbound AAPS -> NS : utcOffset = T.msecs(utcOffset).mins()
 * ```
 *
 * Losing that conversion would not crash and would not look obviously wrong in a log: a record
 * would carry 120 instead of 7200000, which reads as two minutes instead of two hours. It would
 * then go into `contentEqualsTo`, so every record would look changed and sync again, and the wrong
 * value would be uploaded back to Nightscout.
 *
 * These tests pin the units so a rewrite of the wire layer cannot flatten them silently.
 */
internal class UtcOffsetUnitsTest {

    private val plusTwoHoursMs = 7_200_000L   // CEST, what the test server reports as 120
    private val plusTwoHoursMin = 120L

    @Test
    fun `carbs - AAPS milliseconds become Nightscout minutes`() {
        val carbs = CA(timestamp = 10000, isValid = true, amount = 1.0, duration = 0, utcOffset = plusTwoHoursMs)

        assertThat(carbs.toNSCarbs().utcOffset).isEqualTo(plusTwoHoursMin)
    }

    @Test
    fun `carbs - a full round trip keeps the AAPS value`() {
        val carbs = CA(timestamp = 10000, isValid = true, amount = 1.0, duration = 0, utcOffset = plusTwoHoursMs)

        val back = (carbs.toNSCarbs().convertToRemoteAndBack() as NSCarbs).toCarbs()
        assertThat(back.utcOffset).isEqualTo(plusTwoHoursMs)
    }

    @Test
    fun `glucose value - AAPS milliseconds become Nightscout minutes and back`() {
        val gv = GV(
            timestamp = 10000, value = 120.0, isValid = true, utcOffset = plusTwoHoursMs,
            raw = null, trendArrow = TrendArrow.FLAT, noise = null,
            sourceSensor = SourceSensor.DEXCOM_G6_NATIVE, ids = IDs()
        )

        val ns = gv.toNSSvgV3()
        assertThat(ns.utcOffset).isEqualTo(plusTwoHoursMin)
        assertThat(ns.toGV().utcOffset).isEqualTo(plusTwoHoursMs)
    }

    @Test
    fun `offsets that are not whole hours survive`() {
        // India is +05:30, Nepal +05:45, Chatham +12:45 - all whole minutes, none whole hours.
        val cases = mapOf(
            19_800_000L to 330L,   // +05:30
            20_700_000L to 345L,   // +05:45
            45_900_000L to 765L    // +12:45
        )
        for ((ms, minutes) in cases) {
            val carbs = CA(timestamp = 10000, isValid = true, amount = 1.0, duration = 0, utcOffset = ms)
            assertThat(carbs.toNSCarbs().utcOffset).isEqualTo(minutes)
            assertThat((carbs.toNSCarbs().convertToRemoteAndBack() as NSCarbs).toCarbs().utcOffset).isEqualTo(ms)
        }
    }

    @Test
    fun `negative and zero offsets survive`() {
        val cases = mapOf(
            0L to 0L,
            -18_000_000L to -300L,  // -05:00, US Eastern standard time
            -34_200_000L to -570L   // -09:30, Marquesas
        )
        for ((ms, minutes) in cases) {
            val carbs = CA(timestamp = 10000, isValid = true, amount = 1.0, duration = 0, utcOffset = ms)
            assertThat(carbs.toNSCarbs().utcOffset).isEqualTo(minutes)
            assertThat((carbs.toNSCarbs().convertToRemoteAndBack() as NSCarbs).toCarbs().utcOffset).isEqualTo(ms)
        }
    }

    /** A record from another uploader may have no `utcOffset` at all. Inbound that has to become 0, not null. */
    @Test
    fun `a missing Nightscout offset becomes zero`() {
        val ns = NSCarbs(
            date = 10000, device = null, identifier = null, units = null, srvModified = null, srvCreated = null,
            utcOffset = null, subject = null, isReadOnly = false, isValid = true,
            eventType = app.aaps.core.nssdk.localmodel.treatment.EventType.CARBS_CORRECTION,
            notes = null, pumpId = null, endId = null, pumpType = null, pumpSerial = null,
            carbs = 1.0, duration = 0
        )

        assertThat(ns.toCarbs().utcOffset).isEqualTo(0L)
    }

    /** The helpers the converters are built from, checked directly. */
    @Test
    fun `T converts between the two units`() {
        assertThat(T.mins(plusTwoHoursMin).msecs()).isEqualTo(plusTwoHoursMs)
        assertThat(T.msecs(plusTwoHoursMs).mins()).isEqualTo(plusTwoHoursMin)
    }
}

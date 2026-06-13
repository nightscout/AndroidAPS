package app.aaps.pump.omnipod.common.bledriver.pod.state

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * Tests for [OmnipodDashPodStateManagerImpl.integrateExpectedDelivery].
 *
 * All timestamps are constructed from a fixed UTC anchor (2026-01-01T00:00:00Z) so tests are
 * calendar-layout-independent and deterministic. Device timezone is set/restored around each test
 * to isolate [BasalProgram.rateAt] behaviour.
 */
class IntegrateExpectedDeliveryTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config

    private lateinit var sut: OmnipodDashPodStateManagerImpl

    // ---- constants ----------------------------------------------------------------------------

    // 2026-01-01T00:00:00Z
    private val JAN1_UTC = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    private val HOUR  = 3_600_000L
    private val MIN30 = 1_800_000L

    private val OFFSET_UTC    = 0
    private val OFFSET_PLUS2  = 2 * 60 * 60 * 1000   // UTC+2 in ms
    private val OFFSET_MINUS5 = -5 * 60 * 60 * 1000  // UTC-5 in ms

    // ---- setup / teardown ---------------------------------------------------------------------

    private lateinit var savedTz: TimeZone

    @BeforeEach fun setUp() {
        savedTz = TimeZone.getDefault()
        sut = OmnipodDashPodStateManagerImpl(aapsLogger, rxBus, preferences, config)
    }

    @AfterEach fun restoreTz() { TimeZone.setDefault(savedTz) }

    // ---- helpers ------------------------------------------------------------------------------

    private fun seg(startSlot: Int, endSlot: Int, hundredthsPerHour: Int): BasalProgram.Segment =
        BasalProgram.Segment(startSlot.toShort(), endSlot.toShort(), hundredthsPerHour)

    /** Single-rate full-day basal program. */
    private fun flatBasal(rateUh: Double): BasalProgram =
        BasalProgram(listOf(seg(0, 48, (rateUh * 100).toInt())))

    /** Two-segment program with [splitSlot] (0-based 30-min slots) as the split point. */
    private fun twoSegmentBasal(rate1Uh: Double, rate2Uh: Double, splitSlot: Int): BasalProgram =
        BasalProgram(listOf(
            seg(0,         splitSlot, (rate1Uh * 100).toInt()),
            seg(splitSlot, 48,        (rate2Uh * 100).toInt())
        ))

    /** TempBasal anchored to JAN1_UTC + [startOffsetMs]. */
    private fun tempBasal(startOffsetMs: Long, durationMins: Int, rateUh: Double) =
        OmnipodDashPodStateManager.TempBasal(
            startTime         = JAN1_UTC + startOffsetMs,
            durationInMinutes = durationMins.toShort(),
            rate              = rateUh
        )

    // ---- happy path ---------------------------------------------------------------------------

    @Test fun `single rate basal, period within one segment`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // 0.5h @ 1 U/h = 0.5 U
        val result = sut.integrateExpectedDelivery(JAN1_UTC, JAN1_UTC + MIN30, OFFSET_UTC, null, flatBasal(1.0))
        assertThat(result).isNotNull()
        assertThat(result!!).isWithin(1e-9).of(0.5)
    }

    @Test fun `single rate basal, full 24h`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // 24h @ 1 U/h = 24 U
        val result = sut.integrateExpectedDelivery(JAN1_UTC, JAN1_UTC + 24 * HOUR, OFFSET_UTC, null, flatBasal(1.0))
        assertThat(result).isNotNull()
        assertThat(result!!).isWithin(1e-9).of(24.0)
    }

    @Test fun `two-segment basal, period spans segment boundary`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Slot 16 = 08:00; period 07:00-09:00: 1h @ 1 U/h + 1h @ 2 U/h = 3 U
        val prog = twoSegmentBasal(1.0, 2.0, splitSlot = 16)
        val result = sut.integrateExpectedDelivery(JAN1_UTC + 7 * HOUR, JAN1_UTC + 9 * HOUR, OFFSET_UTC, null, prog)
        assertThat(result).isNotNull()
        assertThat(result!!).isWithin(1e-9).of(3.0)
    }

    @Test fun `integration window crosses midnight`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // period 23:00-01:00 (next day); slot 0 boundary at midnight must be projected to Jan2
        // segment 16-48 = 2 U/h, segment 0-16 = 1 U/h → 1h @ 2 + 1h @ 1 = 3 U
        val prog = twoSegmentBasal(1.0, 2.0, splitSlot = 16)
        val result = sut.integrateExpectedDelivery(
            JAN1_UTC + 23 * HOUR, JAN1_UTC + 25 * HOUR,
            OFFSET_UTC, null, prog
        )
        assertThat(result).isNotNull()
        assertThat(result!!).isWithin(1e-9).of(3.0)
    }

    // ---- temp basal --------------------------------------------------------------------------

    @Test fun `temp basal covers entire window`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Temp started 1h before, duration 3h → entire 1h window at 1.5 U/h = 1.5 U
        val tb = tempBasal(-HOUR, 180, 1.5)
        val result = sut.integrateExpectedDelivery(JAN1_UTC, JAN1_UTC + HOUR, OFFSET_UTC, tb, flatBasal(1.0))
        assertThat(result).isNotNull()
        assertThat(result!!).isWithin(1e-9).of(1.5)
    }

    @Test fun `temp basal covers sub-period`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Temp starts at 30 min, lasts 30 min (rate 0); window is 2h
        // 30 min @ 1 U/h + 30 min @ 0 U/h + 60 min @ 1 U/h = 1.5 U
        val tb = tempBasal(MIN30, 30, 0.0)
        val result = sut.integrateExpectedDelivery(JAN1_UTC, JAN1_UTC + 2 * HOUR, OFFSET_UTC, tb, flatBasal(1.0))
        assertThat(result).isNotNull()
        assertThat(result!!).isWithin(1e-9).of(1.5)
    }

    @Test fun `temp basal starts exactly at startTime`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Temp starts at t=0, lasts 30 min; 30 min @ 0 U/h + 30 min @ 1 U/h = 0.5 U
        val tb = tempBasal(0L, 30, 0.0)
        val result = sut.integrateExpectedDelivery(JAN1_UTC, JAN1_UTC + HOUR, OFFSET_UTC, tb, flatBasal(1.0))
        assertThat(result).isNotNull()
        assertThat(result!!).isWithin(1e-9).of(0.5)
    }

    @Test fun `temp basal ends exactly at endTime`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Temp starts at t=0, ends exactly at endTime; entire 1h at temp rate 2 U/h = 2 U
        val tb = tempBasal(0L, 60, 2.0)
        val result = sut.integrateExpectedDelivery(JAN1_UTC, JAN1_UTC + HOUR, OFFSET_UTC, tb, flatBasal(1.0))
        assertThat(result).isNotNull()
        assertThat(result!!).isWithin(1e-9).of(2.0)
    }

    @Test fun `temp basal starts before startTime and ends within window`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Temp started 30 min ago, ends 30 min into window; 30 min @ 0 U/h + 30 min @ 1 U/h = 0.5 U
        val tb = tempBasal(-MIN30, 60, 0.0)
        val result = sut.integrateExpectedDelivery(JAN1_UTC, JAN1_UTC + HOUR, OFFSET_UTC, tb, flatBasal(1.0))
        assertThat(result).isNotNull()
        assertThat(result!!).isWithin(1e-9).of(0.5)
    }

    @Test fun `temp basal starts before startTime and ends after endTime — full window at temp rate`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val tb = tempBasal(-HOUR, 180, 3.0)
        val result = sut.integrateExpectedDelivery(JAN1_UTC, JAN1_UTC + HOUR, OFFSET_UTC, tb, flatBasal(1.0))
        assertThat(result).isNotNull()
        assertThat(result!!).isWithin(1e-9).of(3.0)
    }

    @Test fun `temp basal rate zero for entire window`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val tb = tempBasal(-HOUR, 180, 0.0)
        val result = sut.integrateExpectedDelivery(JAN1_UTC, JAN1_UTC + HOUR, OFFSET_UTC, tb, flatBasal(1.0))
        assertThat(result).isNotNull()
        assertThat(result!!).isWithin(1e-9).of(0.0)
    }

    @Test fun `temp basal spans a scheduled segment boundary — entire window at temp rate`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Temp covers 06:00-10:00, spanning the 08:00 (slot 16) boundary; rate 0.5 over 4h = 2 U
        val prog = twoSegmentBasal(1.0, 2.0, splitSlot = 16)
        val tb = tempBasal(6 * HOUR, 240, 0.5)
        val result = sut.integrateExpectedDelivery(
            JAN1_UTC + 6 * HOUR, JAN1_UTC + 10 * HOUR,
            OFFSET_UTC, tb, prog
        )
        assertThat(result).isNotNull()
        assertThat(result!!).isWithin(1e-9).of(2.0)
    }

    // ---- timezone: pod TZ == device TZ -------------------------------------------------------

    @Test fun `pod timezone UTC, device timezone UTC — baseline`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Slot 16 boundary at 08:00 UTC; 07:00-09:00 = 3 U
        val prog = twoSegmentBasal(1.0, 2.0, splitSlot = 16)
        val result = sut.integrateExpectedDelivery(
            JAN1_UTC + 7 * HOUR, JAN1_UTC + 9 * HOUR,
            OFFSET_UTC, null, prog
        )
        assertThat(result!!).isWithin(1e-9).of(3.0)
    }

    @Test fun `pod timezone UTC+2, device timezone UTC+2 — segment boundaries align`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC+2"))
        // Slot 16 boundary at 08:00 local = 06:00 UTC; window 05:00-07:00 UTC = 3 U
        val prog = twoSegmentBasal(1.0, 2.0, splitSlot = 16)
        val result = sut.integrateExpectedDelivery(
            JAN1_UTC + 5 * HOUR, JAN1_UTC + 7 * HOUR,
            OFFSET_PLUS2, null, prog
        )
        assertThat(result!!).isWithin(1e-9).of(3.0)
    }

    @Test fun `pod timezone UTC-5, device timezone UTC-5 — segment boundaries align`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC-5"))
        // Slot 16 boundary at 08:00 local = 13:00 UTC; window 12:00-14:00 UTC = 3 U
        val prog = twoSegmentBasal(1.0, 2.0, splitSlot = 16)
        val result = sut.integrateExpectedDelivery(
            JAN1_UTC + 12 * HOUR, JAN1_UTC + 14 * HOUR,
            OFFSET_MINUS5, null, prog
        )
        assertThat(result!!).isWithin(1e-9).of(3.0)
    }

    // ---- timezone: pod TZ != device TZ -------------------------------------------------------

    @Test fun `pod timezone UTC, device timezone UTC+2 — segment boundaries still align`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC+2"))
        // Pod is in UTC: slot 16 boundary at 08:00 UTC.
        // Device is UTC+2, so rateAt would see 10:00 local without the adjustment → wrong slot.
        // With podTimeAdjustmentMs = 0 - 7200000 = -7200000 the call is shifted back 2h → correct slot.
        // Window 07:00-09:00 UTC: 1h @ 1 U/h + 1h @ 2 U/h = 3 U
        val prog = twoSegmentBasal(1.0, 2.0, splitSlot = 16)
        val result = sut.integrateExpectedDelivery(
            JAN1_UTC + 7 * HOUR, JAN1_UTC + 9 * HOUR,
            OFFSET_UTC, null, prog
        )
        assertThat(result!!).isWithin(1e-9).of(3.0)
    }

    @Test fun `pod timezone UTC+2, device timezone UTC — segment boundaries still align`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Pod is in UTC+2: slot 16 boundary at 08:00 local = 06:00 UTC.
        // Device is UTC, so rateAt would see 05:30/06:30 UTC directly → wrong slot without adjustment.
        // With podTimeAdjustmentMs = 7200000 - 0 = +7200000 the call is shifted forward 2h → correct slot.
        // Window 05:00-07:00 UTC: 1h @ 1 U/h + 1h @ 2 U/h = 3 U
        val prog = twoSegmentBasal(1.0, 2.0, splitSlot = 16)
        val result = sut.integrateExpectedDelivery(
            JAN1_UTC + 5 * HOUR, JAN1_UTC + 7 * HOUR,
            OFFSET_PLUS2, null, prog
        )
        assertThat(result!!).isWithin(1e-9).of(3.0)
    }

    @Test fun `pod timezone UTC+2, device timezone UTC — window crosses pod midnight`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Pod is in UTC+2: midnight pod-local = 22:00 UTC.
        // Window 21:00-23:00 UTC = 23:00-01:00 pod-local, crossing pod midnight.
        // Segment split at slot 16 (08:00 pod-local): slots 0-16 = 1.0 U/h, slots 16-48 = 2.0 U/h.
        // 21:00-22:00 UTC = 23:00-00:00 pod-local → segment 1 → 1h @ 2.0 U/h
        // 22:00-23:00 UTC = 00:00-01:00 pod-local → segment 0 → 1h @ 1.0 U/h = 3.0 U total
        val prog = twoSegmentBasal(1.0, 2.0, splitSlot = 16)
        val result = sut.integrateExpectedDelivery(
            JAN1_UTC + 21 * HOUR, JAN1_UTC + 23 * HOUR,
            OFFSET_PLUS2, null, prog
        )
        assertThat(result!!).isWithin(1e-9).of(3.0)
    }

    // ---- edge cases --------------------------------------------------------------------------

    @Test fun `startTime equals endTime — returns zero`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val result = sut.integrateExpectedDelivery(JAN1_UTC, JAN1_UTC, OFFSET_UTC, null, flatBasal(1.0))
        assertThat(result).isNotNull()
        assertThat(result!!).isWithin(1e-9).of(0.0)
    }

    @Test fun `startTime after endTime — returns null`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val result = sut.integrateExpectedDelivery(JAN1_UTC + HOUR, JAN1_UTC, OFFSET_UTC, null, flatBasal(1.0))
        assertThat(result).isNull()
    }

    @Test fun `null basal program and no temp basal — returns null`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val result = sut.integrateExpectedDelivery(JAN1_UTC, JAN1_UTC + HOUR, OFFSET_UTC, null, null)
        assertThat(result).isNull()
    }

    @Test fun `null timezone offset — returns null`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val result = sut.integrateExpectedDelivery(JAN1_UTC, JAN1_UTC + HOUR, null, null, flatBasal(1.0))
        assertThat(result).isNull()
    }

    @Test fun `null basal program but temp basal covers full window — returns temp rate`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val tb = tempBasal(-HOUR, 180, 2.0)
        val result = sut.integrateExpectedDelivery(JAN1_UTC, JAN1_UTC + HOUR, OFFSET_UTC, tb, null)
        assertThat(result).isNotNull()
        assertThat(result!!).isWithin(1e-9).of(2.0)
    }

    @Test fun `null basal program and temp basal only covers part of window — returns null`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Temp covers only first 30 min; second 30 min has no rate
        val tb = tempBasal(0L, 30, 2.0)
        val result = sut.integrateExpectedDelivery(JAN1_UTC, JAN1_UTC + HOUR, OFFSET_UTC, tb, null)
        assertThat(result).isNull()
    }
}

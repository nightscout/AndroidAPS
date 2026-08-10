package app.aaps.core.objects.interfaces.utils

import app.aaps.core.interfaces.utils.MidnightTime
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * Pins [MidnightTime] against the `java.time` implementation it is being converted away from.
 *
 * The reference below is a literal copy of the original bodies. Every assertion compares the live
 * [MidnightTime] to it, so the conversion to kotlinx-datetime has to reproduce the old answer
 * exactly rather than merely produce a plausible one.
 *
 * **Why this file exists rather than trusting the existing [MidnightTimeTest].** That suite only
 * asks "is the result midnight, today, in this machine's zone". Every implementation that is roughly
 * right passes it. The interesting cases are the two hours a year when local midnight is not a
 * simple offset from UTC, and this repository has already shipped a fix for exactly that
 * (`calculated day's difference over DST correctly`). So the sweep below runs hour by hour across
 * both transitions.
 *
 * The zone is pinned to Europe/Prague for the same reason the iOS workflow pins it: a CI machine is
 * usually UTC, where every one of these assertions holds trivially because the offset never moves.
 */
class MidnightTimeParityTest {

    private lateinit var original: TimeZone

    @BeforeEach fun pinZone() {
        original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Prague"))
        MidnightTime.resetCache()
    }

    @AfterEach fun restoreZone() {
        TimeZone.setDefault(original)
        MidnightTime.resetCache()
    }

    // ------------------------------------------------------------------ the reference

    private fun referenceCalc(time: Long): Long =
        Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
            .with(LocalTime.of(0, 0, 0, 0))
            .toInstant().toEpochMilli()

    private fun referenceCalcDaysBack(time: Long, daysBack: Long): Long =
        Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
            .with(LocalTime.of(0, 0, 0, 0))
            .minusDays(daysBack)
            .toInstant().toEpochMilli()

    // ------------------------------------------------------------------ sweeps

    /** 2026-03-29 in Europe/Prague: 02:00 CET jumps straight to 03:00 CEST, so 02:xx never happens. */
    private val springForward = Instant.parse("2026-03-28T00:00:00Z").toEpochMilli()

    /** 2026-10-25 in Europe/Prague: 03:00 CEST falls back to 02:00 CET, so 02:xx happens twice. */
    private val fallBack = Instant.parse("2026-10-24T00:00:00Z").toEpochMilli()

    private val hour = 3_600_000L

    private fun sweep(from: Long, hours: Int, label: String) {
        for (i in 0 until hours) {
            val t = from + i * hour
            assertWithMessage("%s: calc(%s)", label, Instant.ofEpochMilli(t))
                .that(MidnightTime.calc(t)).isEqualTo(referenceCalc(t))
        }
    }

    @Test fun `calc matches java time across spring forward`() = sweep(springForward, 72, "spring")

    @Test fun `calc matches java time across fall back`() = sweep(fallBack, 72, "fall")

    @Test fun `calcDaysBack matches java time across spring forward`() {
        for (i in 0 until 72) {
            val t = springForward + i * hour
            for (daysBack in 0L..7L)
                assertWithMessage("spring: calcDaysBack(%s, %s)", Instant.ofEpochMilli(t), daysBack)
                    .that(MidnightTime.calcDaysBack(t, daysBack))
                    .isEqualTo(referenceCalcDaysBack(t, daysBack))
        }
    }

    @Test fun `calcDaysBack matches java time across fall back`() {
        for (i in 0 until 72) {
            val t = fallBack + i * hour
            for (daysBack in 0L..7L)
                assertWithMessage("fall: calcDaysBack(%s, %s)", Instant.ofEpochMilli(t), daysBack)
                    .that(MidnightTime.calcDaysBack(t, daysBack))
                    .isEqualTo(referenceCalcDaysBack(t, daysBack))
        }
    }

    /**
     * Proves the sweeps above are not vacuous.
     *
     * If the default zone failed to apply, or if a zone without daylight saving were chosen, every
     * midnight would sit at the same offset from UTC and the parity assertions would hold for any
     * implementation that simply floored to a whole day. This asserts the opposite: across the
     * spring transition the local midnights really do land on two different UTC offsets, so the
     * sweep is exercising the case it claims to.
     */
    @Test fun `the sweep actually crosses an offset change`() {
        val offsets = (0 until 72)
            .map { springForward + it * hour }
            .map { t -> Math.floorMod(MidnightTime.calc(t), 24 * hour) }
            .toSet()
        assertWithMessage("local midnight must sit at more than one UTC offset across a DST change")
            .that(offsets.size).isGreaterThan(1)
    }

    /**
     * A whole ordinary year, one sample a day, so the sweeps above are not the only evidence and a
     * mistake that only shows outside a transition week still fails.
     */
    @Test fun `calc matches java time over a full year`() {
        val start = Instant.parse("2026-01-01T12:00:00Z").toEpochMilli()
        for (day in 0 until 365) {
            val t = start + day * 24 * hour
            assertWithMessage("day %s (%s)", day, Instant.ofEpochMilli(t))
                .that(MidnightTime.calc(t)).isEqualTo(referenceCalc(t))
        }
    }
}

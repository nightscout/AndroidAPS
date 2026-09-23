package app.aaps.pump.carelevo.domain.ext

import app.aaps.pump.carelevo.domain.model.basal.CarelevoBasalSegmentDomainModel
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.jupiter.api.Test

/**
 * Pure-logic tests for `CarelevoDomainValueExt.kt` — the minute-based basal segment
 * normalizer (`splitSegment`) and `generateUUID`.
 *
 * `splitSegment` always returns exactly 24 hourly buckets whose start/end are in hours;
 * input segment start/end are in MINUTES (divided by 60 internally).
 */
internal class CarelevoDomainValueExtTest {

    private fun seg(startMin: Int, endMin: Int, speed: Double) =
        CarelevoBasalSegmentDomainModel(startTime = startMin, endTime = endMin, speed = speed)

    // ---------- splitSegment ----------

    @Test
    fun `splitSegment always returns 24 hourly buckets`() {
        assertThat(emptyList<CarelevoBasalSegmentDomainModel>().splitSegment()).hasSize(24)
        assertThat(listOf(seg(0, 1440, 1.0)).splitSegment()).hasSize(24)
    }

    @Test
    fun `splitSegment buckets are consecutive one-hour spans`() {
        val hourly = listOf(seg(0, 1440, 1.0)).splitSegment()
        hourly.forEachIndexed { hour, s ->
            assertThat(s.startTime).isEqualTo(hour)
            assertThat(s.endTime).isEqualTo(hour + 1)
        }
    }

    @Test
    fun `splitSegment on empty input fills every hour with zero speed`() {
        val hourly = emptyList<CarelevoBasalSegmentDomainModel>().splitSegment()
        assertThat(hourly.map { it.speed }).containsExactlyElementsIn(List(24) { 0.0 })
    }

    @Test
    fun `splitSegment spreads a full-day segment across all 24 hours`() {
        val hourly = listOf(seg(0, 1440, 2.5)).splitSegment()
        assertThat(hourly.map { it.speed }).containsExactlyElementsIn(List(24) { 2.5 })
        assertThat(hourly.first()).isEqualTo(seg(0, 1, 2.5))
        assertThat(hourly.last()).isEqualTo(seg(23, 24, 2.5))
    }

    @Test
    fun `splitSegment splits a morning and afternoon segment at noon`() {
        val hourly = listOf(
            seg(0, 720, 1.0),      // 00:00 - 12:00
            seg(720, 1440, 2.0)    // 12:00 - 24:00
        ).splitSegment()

        for (hour in 0..11) assertThat(hourly[hour].speed).isEqualTo(1.0)
        for (hour in 12..23) assertThat(hourly[hour].speed).isEqualTo(2.0)
    }

    @Test
    fun `splitSegment leaves uncovered hours at zero speed`() {
        // Only 02:00 - 04:00 covered.
        val hourly = listOf(seg(120, 240, 3.0)).splitSegment()
        assertThat(hourly[0].speed).isEqualTo(0.0)
        assertThat(hourly[1].speed).isEqualTo(0.0)
        assertThat(hourly[2].speed).isEqualTo(3.0)
        assertThat(hourly[3].speed).isEqualTo(3.0)
        assertThat(hourly[4].speed).isEqualTo(0.0)
    }

    @Test
    fun `splitSegment gives the last matching segment priority for overlaps`() {
        // seg1 covers 00:00-06:00 @1.0, seg2 covers 02:00-04:00 @9.0.
        val hourly = listOf(
            seg(0, 360, 1.0),
            seg(120, 240, 9.0)
        ).splitSegment()

        assertThat(hourly[1].speed).isEqualTo(1.0) // only seg1
        assertThat(hourly[2].speed).isEqualTo(9.0) // overlap -> later-start wins
        assertThat(hourly[3].speed).isEqualTo(9.0) // overlap -> later-start wins
        assertThat(hourly[5].speed).isEqualTo(1.0) // only seg1
    }

    @Test
    fun `splitSegment sorts unsorted input before bucketing`() {
        val hourly = listOf(
            seg(720, 1440, 2.0),   // afternoon first in the list
            seg(0, 720, 1.0)       // morning second
        ).splitSegment()

        assertThat(hourly[0].speed).isEqualTo(1.0)
        assertThat(hourly[23].speed).isEqualTo(2.0)
    }

    @Test
    fun `splitSegment clamps a start hour above 23 into the last hour`() {
        // 1500 min -> 25h, clamped start 23; end clamped to 24 -> covers only hour 23.
        val hourly = listOf(seg(1500, 1500, 4.0)).splitSegment()
        assertThat(hourly[22].speed).isEqualTo(0.0)
        assertThat(hourly[23].speed).isEqualTo(4.0)
    }

    @Test
    fun `splitSegment clamps a negative start hour up to zero`() {
        // -120 min -> -2h clamped to 0; end 120 min -> 2h. Covers hours 0 and 1.
        val hourly = listOf(seg(-120, 120, 3.0)).splitSegment()
        assertThat(hourly[0].speed).isEqualTo(3.0)
        assertThat(hourly[1].speed).isEqualTo(3.0)
        assertThat(hourly[2].speed).isEqualTo(0.0)
    }

    @Test
    fun `splitSegment enforces a minimum one-hour span when end is not after start`() {
        // start 10h, end 10h -> end coerced to start+1 = 11h. Covers only hour 10.
        val hourly = listOf(seg(600, 600, 5.0)).splitSegment()
        assertThat(hourly[9].speed).isEqualTo(0.0)
        assertThat(hourly[10].speed).isEqualTo(5.0)
        assertThat(hourly[11].speed).isEqualTo(0.0)
    }

    // ---------- generateUUID ----------

    @Test
    fun `generateUUID returns a canonical 36-char UUID string`() {
        val uuid = generateUUID()
        assertThat(uuid).hasLength(36)
        // Parsing back succeeds and re-stringifies to the same value.
        assertThat(UUID.fromString(uuid).toString()).isEqualTo(uuid)
    }

    @Test
    fun `generateUUID returns distinct values on successive calls`() {
        assertThat(generateUUID()).isNotEqualTo(generateUUID())
    }
}

package info.nightscout.androidaps.interaction.utils

import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.FakeWearUtil
import info.nightscout.androidaps.WearTestBase
import org.junit.jupiter.api.Test

/**
 * Created by dlvoy on 22.11.2019.
 */
@Suppress("SpellCheckingInspection")
class WearUtilTest : WearTestBase() {

    @Test fun timestampAndTimeDiffsTest() {

        // smoke for mocks - since we freeze "now" to get stable tests
        assertThat(fakeWearUtil.timestamp()).isEqualTo(FakeWearUtil.REF_NOW)
        assertThat(fakeWearUtil.msTill(FakeWearUtil.REF_NOW)).isEqualTo(0L)
        assertThat(fakeWearUtil.msTill(FakeWearUtil.REF_NOW + 3456L)).isEqualTo(3456L)
        assertThat(fakeWearUtil.msTill(FakeWearUtil.REF_NOW - 6294L)).isEqualTo(-6294L)
        assertThat(fakeWearUtil.msTill(FakeWearUtil.REF_NOW)).isEqualTo(0L)
        assertThat(fakeWearUtil.msSince(FakeWearUtil.REF_NOW + 3456L)).isEqualTo(-3456L)
        assertThat(fakeWearUtil.msSince(FakeWearUtil.REF_NOW - 6294L)).isEqualTo(6294L)
    }

    @Test fun joinSetTest() {
        // GIVEN
        val refSet = setOf("element1", "second-elem", "3rd")

        // WHEN
        val joined = persistence.joinSet(refSet, "|")

        // THEN
        // we cannot guarantee order of items in joined string
        // but all items have to be there
        assertThat(joined).hasLength(refSet.sumOf { it.length } + (refSet.size - 1))
        assertThat("|$joined|").contains("|element1|")
        assertThat("|$joined|").contains("|second-elem|")
        assertThat("|$joined|").contains("|3rd|")
    }

    @Test fun explodeSetTest() {
        // GIVEN
        val serializedSet = "second-elem:element1:3rd"

        // WHEN
        val set = persistence.explodeSet(serializedSet, ":")

        // THEN
        assertThat(set).containsExactly("element1", "second-elem", "3rd")
    }

    @Test fun explodeSetEmptyElemsTest() {
        // GIVEN
        val serializedSet = ",,,,real,,,another,,,"

        // WHEN
        val set = persistence.explodeSet(serializedSet, ",")

        // THEN
        assertThat(set).containsExactly("real", "another")
    }

    @Test fun joinExplodeStabilityTest() {
        // GIVEN
        val refSet = setOf(
            "element1",
            "second-elem",
            "3rd",
            "czwarty",
            "V",
            "6"
        )

        // WHEN
        val joinedSet = persistence.joinSet(refSet, "#")
        val explodedSet = persistence.explodeSet(joinedSet, "#")

        // THEN
        assertThat(explodedSet).containsExactlyElementsIn(refSet)
    }

    /* Mike: failing with new mockito
        @Test fun threadSleepTest() {
            // GIVEN
            val testStart = System.currentTimeMillis()
            val requestedSleepDuration = 85L
            val measuringMargin = 100L

            // WHEN
            WearUtil.threadSleep(requestedSleepDuration)
            val measuredSleepDuration = System.currentTimeMillis() - testStart

            // THEN
            // we cannot guarantee to be exact to the millisecond - we add some margin of error
            assertThat(measuredSleepDuration).isLessThan(60L)
            assertThat(measuredSleepDuration).isGreaterThan(requestedSleepDuration + measuringMargin)
        }
    */
    @Test fun rateLimitTest() {
        // WHEN
        val firstCall = fakeWearUtil.isBelowRateLimit("test-limit", 3)
        val callAfterward = fakeWearUtil.isBelowRateLimit("test-limit", 3)
        fakeWearUtil.progressClock(500L)
        val callTooSoon = fakeWearUtil.isBelowRateLimit("test-limit", 3)
        fakeWearUtil.progressClock(3100L)
        val callAfterRateLimit = fakeWearUtil.isBelowRateLimit("test-limit", 3)

        // THEN
        assertThat(firstCall).isTrue()
        assertThat(callAfterward).isFalse()
        assertThat(callTooSoon).isFalse()
        assertThat(callAfterRateLimit).isTrue()
    }
}

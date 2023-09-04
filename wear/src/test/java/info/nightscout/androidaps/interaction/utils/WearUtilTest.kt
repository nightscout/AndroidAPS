package info.nightscout.androidaps.interaction.utils

import info.nightscout.androidaps.WearTestBase
import info.nightscout.androidaps.testing.mockers.WearUtilMocker
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Created by dlvoy on 22.11.2019.
 */
@Suppress("SpellCheckingInspection")
class WearUtilTest : WearTestBase() {

    @Test fun timestampAndTimeDiffsTest() {

        // smoke for mocks - since we freeze "now" to get stable tests
        Assertions.assertEquals(WearUtilMocker.REF_NOW, wearUtil.timestamp())
        Assertions.assertEquals(0L, wearUtil.msTill(WearUtilMocker.REF_NOW))
        Assertions.assertEquals(3456L, wearUtil.msTill(WearUtilMocker.REF_NOW + 3456L))
        Assertions.assertEquals(-6294L, wearUtil.msTill(WearUtilMocker.REF_NOW - 6294L))
        Assertions.assertEquals(0L, wearUtil.msTill(WearUtilMocker.REF_NOW))
        Assertions.assertEquals(-3456L, wearUtil.msSince(WearUtilMocker.REF_NOW + 3456L))
        Assertions.assertEquals(6294L, wearUtil.msSince(WearUtilMocker.REF_NOW - 6294L))
    }

    @Test fun joinSetTest() {
        // GIVEN
        val refSet: MutableSet<String> = HashSet()
        refSet.add("element1")
        refSet.add("second-elem")
        refSet.add("3rd")

        // WHEN
        val joined = persistence.joinSet(refSet, "|")

        // THEN
        // we cannot guarantee order of items in joined string
        // but all items have to be there
        Assertions.assertEquals(joined.length, "element1".length + "second-elem".length + "3rd".length + "|".length * 2)
        Assertions.assertTrue("|$joined|".contains("|" + "element1" + "|"))
        Assertions.assertTrue("|$joined|".contains("|" + "second-elem" + "|"))
        Assertions.assertTrue("|$joined|".contains("|" + "3rd" + "|"))
    }

    @Test fun explodeSetTest() {
        // GIVEN
        val serializedSet = "second-elem:element1:3rd"

        // WHEN
        val set = persistence.explodeSet(serializedSet, ":")

        // THEN
        Assertions.assertEquals(set.size, 3)
        Assertions.assertTrue(set.contains("element1"))
        Assertions.assertTrue(set.contains("second-elem"))
        Assertions.assertTrue(set.contains("3rd"))
    }

    @Test fun explodeSetEmptyElemsTest() {
        // GIVEN
        val serializedSet = ",,,,real,,,another,,,"

        // WHEN
        val set = persistence.explodeSet(serializedSet, ",")

        // THEN
        Assertions.assertEquals(set.size, 2)
        Assertions.assertEquals(true, set.contains("real"))
        Assertions.assertEquals(true, set.contains("another"))
    }

    @Test fun joinExplodeStabilityTest() {
        // GIVEN
        val refSet: MutableSet<String> = HashSet()
        refSet.add("element1")
        refSet.add("second-elem")
        refSet.add("3rd")
        refSet.add("czwarty")
        refSet.add("V")
        refSet.add("6")

        // WHEN
        val joinedSet = persistence.joinSet(refSet, "#")
        val explodedSet = persistence.explodeSet(joinedSet, "#")

        // THEN
        Assertions.assertEquals(explodedSet, refSet)
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
            Assertions.assertTrue(60L > measuredSleepDuration)
            Assertions.assertTrue(requestedSleepDuration + measuringMargin < measuredSleepDuration)
        }
    */
    @Test fun rateLimitTest() {
        wearUtilMocker.prepareMockNoReal()
        // WHEN
        val firstCall = wearUtil.isBelowRateLimit("test-limit", 3)
        val callAfterward = wearUtil.isBelowRateLimit("test-limit", 3)
        wearUtilMocker.progressClock(500L)
        val callTooSoon = wearUtil.isBelowRateLimit("test-limit", 3)
        wearUtilMocker.progressClock(3100L)
        val callAfterRateLimit = wearUtil.isBelowRateLimit("test-limit", 3)

        // THEN
        Assertions.assertTrue(firstCall)
        Assertions.assertFalse(callAfterward)
        Assertions.assertFalse(callTooSoon)
        Assertions.assertTrue(callAfterRateLimit)
    }
}
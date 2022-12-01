package info.nightscout.androidaps.interaction.utils

import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.testing.mockers.WearUtilMocker
import org.junit.Assert
import org.junit.jupiter.api.Test

/**
 * Created by dlvoy on 22.11.2019.
 */
@Suppress("SpellCheckingInspection")
class WearUtilTest : TestBase() {

    @Test fun timestampAndTimeDiffsTest() {

        // smoke for mocks - since we freeze "now" to get stable tests
        Assert.assertEquals(WearUtilMocker.REF_NOW, wearUtil.timestamp())
        Assert.assertEquals(0L, wearUtil.msTill(WearUtilMocker.REF_NOW))
        Assert.assertEquals(3456L, wearUtil.msTill(WearUtilMocker.REF_NOW + 3456L))
        Assert.assertEquals(-6294L, wearUtil.msTill(WearUtilMocker.REF_NOW - 6294L))
        Assert.assertEquals(0L, wearUtil.msTill(WearUtilMocker.REF_NOW))
        Assert.assertEquals(-3456L, wearUtil.msSince(WearUtilMocker.REF_NOW + 3456L))
        Assert.assertEquals(6294L, wearUtil.msSince(WearUtilMocker.REF_NOW - 6294L))
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
        Assert.assertEquals(joined.length, "element1".length + "second-elem".length + "3rd".length + "|".length * 2)
        Assert.assertTrue("|$joined|".contains("|" + "element1" + "|"))
        Assert.assertTrue("|$joined|".contains("|" + "second-elem" + "|"))
        Assert.assertTrue("|$joined|".contains("|" + "3rd" + "|"))
    }

    @Test fun explodeSetTest() {
        // GIVEN
        val serializedSet = "second-elem:element1:3rd"

        // WHEN
        val set = persistence.explodeSet(serializedSet, ":")

        // THEN
        Assert.assertEquals(set.size, 3)
        Assert.assertTrue(set.contains("element1"))
        Assert.assertTrue(set.contains("second-elem"))
        Assert.assertTrue(set.contains("3rd"))
    }

    @Test fun explodeSetEmptyElemsTest() {
        // GIVEN
        val serializedSet = ",,,,real,,,another,,,"

        // WHEN
        val set = persistence.explodeSet(serializedSet, ",")

        // THEN
        Assert.assertEquals(set.size, 2)
        Assert.assertEquals(true, set.contains("real"))
        Assert.assertEquals(true, set.contains("another"))
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
        Assert.assertEquals(explodedSet, refSet)
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
            Assert.assertTrue(60L > measuredSleepDuration)
            Assert.assertTrue(requestedSleepDuration + measuringMargin < measuredSleepDuration)
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
        Assert.assertTrue(firstCall)
        Assert.assertFalse(callAfterward)
        Assert.assertFalse(callTooSoon)
        Assert.assertTrue(callAfterRateLimit)
    }
}
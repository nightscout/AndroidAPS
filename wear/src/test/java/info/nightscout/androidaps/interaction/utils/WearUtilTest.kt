package info.nightscout.androidaps.interaction.utils

import android.util.Log
import com.google.android.gms.wearable.DataMap
import info.nightscout.androidaps.testing.mockers.LogMocker
import info.nightscout.androidaps.testing.mockers.WearUtilMocker
import info.nightscout.androidaps.testing.mocks.BundleMock
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

/**
 * Created by dlvoy on 22.11.2019.
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(WearUtil::class, Log::class)
class WearUtilTest {

    @Before @Throws(Exception::class) fun mock() {
        WearUtilMocker.prepareMock()
        LogMocker.prepareMock()
    }

    @Test fun timestampAndTimeDiffsTest() {

        // smoke for mocks - since we freeze "now" to get stable tests
        Assert.assertThat(WearUtilMocker.REF_NOW, CoreMatchers.`is`(WearUtil.timestamp()))
        Assert.assertThat(0L, CoreMatchers.`is`(WearUtil.msTill(WearUtilMocker.REF_NOW)))
        Assert.assertThat(3456L, CoreMatchers.`is`(WearUtil.msTill(WearUtilMocker.REF_NOW + 3456L)))
        Assert.assertThat(-6294L, CoreMatchers.`is`(WearUtil.msTill(WearUtilMocker.REF_NOW - 6294L)))
        Assert.assertThat(0L, CoreMatchers.`is`(WearUtil.msTill(WearUtilMocker.REF_NOW)))
        Assert.assertThat(-3456L, CoreMatchers.`is`(WearUtil.msSince(WearUtilMocker.REF_NOW + 3456L)))
        Assert.assertThat(6294L, CoreMatchers.`is`(WearUtil.msSince(WearUtilMocker.REF_NOW - 6294L)))
    }

    @Test fun joinSetTest() {
        // GIVEN
        val refSet: MutableSet<String> = HashSet()
        refSet.add("element1")
        refSet.add("second-elem")
        refSet.add("3rd")

        // WHEN
        val joined = WearUtil.joinSet(refSet, "|")

        // THEN
        // we cannot guarantee order of items in joined string
        // but all items have to be there
        Assert.assertThat(joined.length, CoreMatchers.`is`("element1".length + "second-elem".length + "3rd".length + "|".length * 2))
        Assert.assertThat("|$joined|", CoreMatchers.containsString("|" + "element1" + "|"))
        Assert.assertThat("|$joined|", CoreMatchers.containsString("|" + "second-elem" + "|"))
        Assert.assertThat("|$joined|", CoreMatchers.containsString("|" + "3rd" + "|"))
    }

    @Test fun explodeSetTest() {
        // GIVEN
        val serializedSet = "second-elem:element1:3rd"

        // WHEN
        val set = WearUtil.explodeSet(serializedSet, ":")

        // THEN
        Assert.assertThat(set.size, CoreMatchers.`is`(3))
        Assert.assertTrue(set.contains("element1"))
        Assert.assertTrue(set.contains("second-elem"))
        Assert.assertTrue(set.contains("3rd"))
    }

    @Test fun explodeSetEmptyElemsTest() {
        // GIVEN
        val serializedSet = ",,,,real,,,another,,,"

        // WHEN
        val set = WearUtil.explodeSet(serializedSet, ",")

        // THEN
        Assert.assertThat(set.size, CoreMatchers.`is`(2))
        Assert.assertThat(true, CoreMatchers.`is`(set.contains("real")))
        Assert.assertThat(true, CoreMatchers.`is`(set.contains("another")))
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
        val joinedSet = WearUtil.joinSet(refSet, "#")
        val explodedSet = WearUtil.explodeSet(joinedSet, "#")

        // THEN
        Assert.assertThat(explodedSet, CoreMatchers.`is`(refSet))
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
        // WHEN
        val firstCall = WearUtil.isBelowRateLimit("test-limit", 3)
        val callAfterward = WearUtil.isBelowRateLimit("test-limit", 3)
        WearUtilMocker.progressClock(500L)
        val callTooSoon = WearUtil.isBelowRateLimit("test-limit", 3)
        WearUtilMocker.progressClock(3100L)
        val callAfterRateLimit = WearUtil.isBelowRateLimit("test-limit", 3)

        // THEN
        Assert.assertTrue(firstCall)
        Assert.assertFalse(callAfterward)
        Assert.assertFalse(callTooSoon)
        Assert.assertTrue(callAfterRateLimit)
    }

    /**
     * It tests if mock for bundleToDataMap is sane,
     * because original impl. of bundleToDataMap
     * uses DataMap.fromBundle which need Android SDK runtime
     */
    @Test @Throws(Exception::class) fun bundleToDataMapTest() {
        // GIVEN
        val refMap = DataMap()
        refMap.putString("ala", "ma kota")
        refMap.putInt("why", 42)
        refMap.putFloatArray("list", floatArrayOf(0.45f, 3.2f, 6.8f))

        // WHEN
        WearUtilMocker.prepareMockNoReal()
        val bundle = BundleMock.mock(refMap)
        val gotMap = WearUtil.bundleToDataMap(bundle)

        // THEN
        Assert.assertThat(gotMap, CoreMatchers.`is`(refMap))
    }
}
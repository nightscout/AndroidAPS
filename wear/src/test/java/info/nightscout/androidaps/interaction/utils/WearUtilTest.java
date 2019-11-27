package info.nightscout.androidaps.interaction.utils;

import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.wearable.DataMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashSet;
import java.util.Set;

import info.nightscout.androidaps.testing.mockers.LogMocker;
import info.nightscout.androidaps.testing.mockers.WearUtilMocker;
import info.nightscout.androidaps.testing.mocks.BundleMock;

import static info.nightscout.androidaps.testing.mockers.WearUtilMocker.REF_NOW;
import static org.hamcrest.CoreMatchers.both;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Created by dlvoy on 22.11.2019.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( { WearUtil.class, Log.class} )
public class WearUtilTest {

    @Before
    public void mock() throws Exception {
        WearUtilMocker.prepareMock();
        LogMocker.prepareMock();
    }

    @Test
    public void timestampAndTimeDiffsTest() {

        // smoke for mocks - since we freeze "now" to get stable tests
        assertThat(REF_NOW, is(WearUtil.timestamp()));

        assertThat(0L, is(WearUtil.msTill(REF_NOW)));
        assertThat(3456L, is(WearUtil.msTill(REF_NOW+3456L)));
        assertThat(-6294L, is(WearUtil.msTill(REF_NOW-6294L)));

        assertThat(0L, is(WearUtil.msTill(REF_NOW)));
        assertThat(-3456L, is(WearUtil.msSince(REF_NOW+3456L)));
        assertThat(6294L, is(WearUtil.msSince(REF_NOW-6294L)));
    }

    @Test
    public void joinSetTest() {
        // GIVEN
        Set<String> refSet = new HashSet<>();
        refSet.add("element1");
        refSet.add("second-elem");
        refSet.add("3rd");

        // WHEN
        String joined = WearUtil.joinSet(refSet, "|");

        // THEN
        // we cannot guarantee order of items in joined string
        // but all items have to be there
        assertThat(joined.length(), is("element1".length() + "second-elem".length() + "3rd".length() + "|".length()*2 ));

        assertThat("|"+joined+"|", containsString("|"+"element1"+"|"));
        assertThat("|"+joined+"|", containsString("|"+"second-elem"+"|"));
        assertThat("|"+joined+"|", containsString("|"+"3rd"+"|"));
    }

    @Test
    public void explodeSetTest() {
        // GIVEN
        String serializedSet = "second-elem:element1:3rd";

        // WHEN
        Set<String> set = WearUtil.explodeSet(serializedSet, ":");

        // THEN
        assertThat(set.size(), is(3));

        assertTrue(set.contains("element1"));
        assertTrue(set.contains("second-elem"));
        assertTrue(set.contains("3rd"));
    }

    @Test
    public void explodeSetEmptyElemsTest() {
        // GIVEN
        String serializedSet = ",,,,real,,,another,,,";

        // WHEN
        Set<String> set = WearUtil.explodeSet(serializedSet, ",");

        // THEN
        assertThat(set.size(), is(2));

        assertThat(true, is(set.contains("real")));
        assertThat(true, is(set.contains("another")));
    }

    @Test
    public void joinExplodeStabilityTest() {
        // GIVEN
        Set<String> refSet = new HashSet<>();
        refSet.add("element1");
        refSet.add("second-elem");
        refSet.add("3rd");
        refSet.add("czwarty");
        refSet.add("V");
        refSet.add("6");

        // WHEN
        String joinedSet = WearUtil.joinSet(refSet, "#");
        final Set<String> explodedSet = WearUtil.explodeSet(joinedSet, "#");

        // THEN
        assertThat(explodedSet, is(refSet));
    }

    @Test
    public void threadSleepTest() {
        // GIVEN
        final long testStart = System.currentTimeMillis();
        final long requestedSleepDuration = 85L;
        final long measuringMargin = 100L;

        // WHEN
        WearUtil.threadSleep(requestedSleepDuration);
        final long measuredSleepDuration = System.currentTimeMillis() - testStart;

        // THEN
        // we cannot guarantee to be exact to the millisecond - we add some margin of error
        assertThat(measuredSleepDuration, is(both(greaterThan(60L)).and(lessThan(requestedSleepDuration+measuringMargin))));
    }

    @Test
    public void rateLimitTest() {
        // WHEN
        final boolean firstCall = WearUtil.isBelowRateLimit("test-limit", 3);
        final boolean callAfterward = WearUtil.isBelowRateLimit("test-limit", 3);
        WearUtilMocker.progressClock(500L);
        final boolean callTooSoon = WearUtil.isBelowRateLimit("test-limit", 3);
        WearUtilMocker.progressClock(3100L);
        final boolean callAfterRateLimit = WearUtil.isBelowRateLimit("test-limit", 3);

        // THEN
        assertTrue(firstCall);
        assertFalse(callAfterward);
        assertFalse(callTooSoon);
        assertTrue(callAfterRateLimit);
    }

    /**
     * It tests if mock for bundleToDataMap is sane,
     * because original impl. of bundleToDataMap
     * uses DataMap.fromBundle which need Android SDK runtime
     */
    @Test
    public void bundleToDataMapTest() throws Exception {
        // GIVEN
        DataMap refMap = new DataMap();
        refMap.putString("ala", "ma kota");
        refMap.putInt("why", 42);
        refMap.putFloatArray("list", new float[]{0.45f, 3.2f, 6.8f});

        // WHEN
        WearUtilMocker.prepareMockNoReal();
        Bundle bundle = BundleMock.mock(refMap);
        DataMap gotMap = WearUtil.bundleToDataMap(bundle);

        // THEN
        assertThat(gotMap, is(refMap));
    }


}
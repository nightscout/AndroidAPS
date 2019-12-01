package info.nightscout.androidaps.data;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashSet;
import java.util.Set;

import info.nightscout.androidaps.interaction.utils.Constants;
import info.nightscout.androidaps.interaction.utils.WearUtil;
import info.nightscout.androidaps.testing.mockers.WearUtilMocker;

import static org.hamcrest.number.OrderingComparison.comparesEqualTo;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest( { WearUtil.class } )
public class BgWatchDataTest {

    @Before
    public void mock() throws Exception {
        WearUtilMocker.prepareMockNoReal();
    }

    @Test
    public void bgWatchDataHashTest() {
        // GIVEN
        BgWatchData inserted = new BgWatchData(
                88.0, 160.0, 90.0,
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*4*1, 1
        );
        Set<BgWatchData> set = new HashSet<>();

        // THEN
        assertFalse(set.contains(inserted));
        set.add(inserted);
        assertTrue(set.contains(inserted));
    }

    /**
     * BgWatchData has BIZARRE equals - only timestamp and color are checked!
     */
    @Test
    public void bgWatchDataEqualsTest() {
        // GIVEN
        BgWatchData item1 = new BgWatchData(
                88.0, 160.0, 90.0,
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS, 1
        );

        BgWatchData item2sameTimeSameColor = new BgWatchData(
                123.0, 190, 90.0,
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS, 1
        );

        BgWatchData item3sameTimeSameDiffColor = new BgWatchData(
                96.0, 190, 90.0,
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS, 0
        );
        BgWatchData item4differentTime = new BgWatchData(
                88.0, 160.0, 90.0,
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*2, 1
        );

        // THEN
        assertEquals(item1, item2sameTimeSameColor);
        assertNotEquals(item1, item3sameTimeSameDiffColor);
        assertNotEquals(item1, item4differentTime);

        assertFalse(item1.equals("aa bbb"));
    }

    /**
     * BgWatchData is ordered by timestamp, reverse order
     */
    @Test
    public void bgWatchDataCompareTest() {
        // GIVEN
        BgWatchData item1 = new BgWatchData(
                85, 160.0, 90.0,
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*2, 1
        );

        BgWatchData item2 = new BgWatchData(
                80, 190, 90.0,
                WearUtilMocker.REF_NOW, 1
        );

        BgWatchData item3 = new BgWatchData(
                80, 190, 50.0,
                WearUtilMocker.REF_NOW + Constants.MINUTE_IN_MS*5, 0
        );

        BgWatchData item4 = new BgWatchData(
                160, 140, 70.0,
                WearUtilMocker.REF_NOW, 0
        );

        // THEN
        assertThat(item2, lessThan(item1));
        assertThat(item2, greaterThan(item3));
        assertThat(item2, comparesEqualTo(item4));
    }
}

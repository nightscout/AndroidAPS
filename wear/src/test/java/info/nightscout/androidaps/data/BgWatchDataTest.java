package info.nightscout.androidaps.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import info.nightscout.androidaps.TestBase;
import info.nightscout.androidaps.interaction.utils.Constants;
import info.nightscout.androidaps.testing.mockers.WearUtilMocker;

public class BgWatchDataTest extends TestBase {

    @Test
    public void bgWatchDataHashTest() {
        // GIVEN
        BgWatchData inserted = new BgWatchData(
                88.0, 160.0, 90.0,
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 4, 1
        );
        Set<BgWatchData> set = new HashSet<>();

        // THEN
        //noinspection ConstantConditions
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
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 2, 1
        );

        // THEN
        assertEquals(item1, item2sameTimeSameColor);
        assertNotEquals(item1, item3sameTimeSameDiffColor);
        assertNotEquals(item1, item4differentTime);
    }

    /**
     * BgWatchData is ordered by timestamp, reverse order
     */
    @Test
    public void bgWatchDataCompareTest() {
        // GIVEN
        BgWatchData item1 = new BgWatchData(
                85, 160.0, 90.0,
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 2, 1
        );

        BgWatchData item2 = new BgWatchData(
                80, 190, 90.0,
                WearUtilMocker.REF_NOW, 1
        );

        BgWatchData item3 = new BgWatchData(
                80, 190, 50.0,
                WearUtilMocker.REF_NOW + Constants.MINUTE_IN_MS * 5, 0
        );

        BgWatchData item4 = new BgWatchData(
                160, 140, 70.0,
                WearUtilMocker.REF_NOW, 0
        );

        // THEN
        assertTrue(item2.compareTo(item1) < 0);
        assertTrue(item2.compareTo(item3) > 0);
        assertEquals(0, item2.compareTo(item4));
    }
}

package info.nightscout.androidaps.data;

import static org.junit.Assert.assertEquals;

import com.google.android.gms.wearable.DataMap;

import org.junit.Test;

import java.util.ArrayList;

import info.nightscout.androidaps.TestBase;
import info.nightscout.androidaps.interaction.utils.Constants;
import info.nightscout.androidaps.testing.mockers.WearUtilMocker;
import info.nightscout.androidaps.testing.utils.BgWatchDataExt;

@SuppressWarnings("PointlessArithmeticExpression")
public class RawDisplayDataBgEntriesTest extends TestBase {

    //==============================================================================================
    // ENTRIES for chart
    //==============================================================================================

    private DataMap dataMapForEntries() {

        DataMap dataMap = new DataMap();
        ArrayList<DataMap> entries = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            DataMap entry = new DataMap();
            entry.putLong("timestamp", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 4 * (16 - i));
            entry.putDouble("sgvDouble", 145.0 - 5 * i);
            entry.putDouble("high", 170.0);
            entry.putDouble("low", 80.0);
            entry.putInt("color", 0);
            entries.add(entry);
        }
        dataMap.putDataMapArrayList("entries", entries);

        return dataMap;
    }

    private DataMap dataMapForEntries(long timestamp, double sgv) {
        DataMap entry = new DataMap();
        entry.putLong("timestamp", timestamp);
        entry.putDouble("sgvDouble", sgv);
        entry.putDouble("high", 160.0);
        entry.putDouble("low", 90.0);
        entry.putInt("color", 1);
        return entry;
    }

    @Test
    public void addToWatchSetTest() {
        // GIVEN
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());
        DataMap multipleEntries = dataMapForEntries();
        DataMap singleEntry1 = dataMapForEntries(WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 4 * 2, 92);
        DataMap singleEntry2 = dataMapForEntries(WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 4 * 1, 88);

        // WHEN, THEN
        // add list
        newRaw.addToWatchSet(multipleEntries);
        assertEquals(newRaw.bgDataList.size(), 12);

        assertEquals(new BgWatchDataExt(newRaw.bgDataList.get(5)),
                new BgWatchDataExt(new BgWatchData(
                        120.0, 170.0, 80.0,
                        WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 4 * (16 - 5), 0
                )));

        assertEquals(new BgWatchDataExt(newRaw.bgDataList.get(11)),
                new BgWatchDataExt(new BgWatchData(
                        90.0, 170.0, 80.0,
                        WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 4 * (16 - 11), 0
                )));

        // add single entries
        newRaw.addToWatchSet(singleEntry1);
        newRaw.addToWatchSet(singleEntry2);
        assertEquals(newRaw.bgDataList.size(), 14);

        assertEquals(new BgWatchDataExt(newRaw.bgDataList.get(12)),
                new BgWatchDataExt(new BgWatchData(
                        92.0, 160.0, 90.0,
                        WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 4 * 2, 1
                )));
        assertEquals(new BgWatchDataExt(newRaw.bgDataList.get(13)),
                new BgWatchDataExt(new BgWatchData(
                        88.0, 160.0, 90.0,
                        WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 4 * 1, 1
                )));

        // ignore duplicates
        newRaw.addToWatchSet(singleEntry2);
        assertEquals(newRaw.bgDataList.size(), 14);
    }

    @Test
    public void addToWatchSetCleanupOldTest() {
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        newRaw.addToWatchSet(dataMapForEntries(getWearUtil().timestamp(), 125));
        assertEquals(newRaw.bgDataList.size(), 1);

        getWearUtilMocker().progressClock(Constants.HOUR_IN_MS * 2);
        newRaw.addToWatchSet(dataMapForEntries(getWearUtil().timestamp(), 140));
        assertEquals(newRaw.bgDataList.size(), 2);

        getWearUtilMocker().progressClock(Constants.HOUR_IN_MS * 1);
        newRaw.addToWatchSet(dataMapForEntries(getWearUtil().timestamp(), 150));
        getWearUtilMocker().progressClock(Constants.HOUR_IN_MS * 1 + Constants.MINUTE_IN_MS * 30);
        newRaw.addToWatchSet(dataMapForEntries(getWearUtil().timestamp(), 101));
        assertEquals(newRaw.bgDataList.size(), 4);

        getWearUtilMocker().progressClock(Constants.MINUTE_IN_MS * 30);
        newRaw.addToWatchSet(dataMapForEntries(getWearUtil().timestamp(), 90));
        assertEquals(newRaw.bgDataList.size(), 5);

        getWearUtilMocker().progressClock(Constants.HOUR_IN_MS * 1 + Constants.MINUTE_IN_MS * 30);
        newRaw.addToWatchSet(dataMapForEntries(getWearUtil().timestamp(), 80));
        assertEquals(newRaw.bgDataList.size(), 5);

        getWearUtilMocker().progressClock(Constants.HOUR_IN_MS * 4);
        newRaw.addToWatchSet(dataMapForEntries(getWearUtil().timestamp(), 92));
        assertEquals(newRaw.bgDataList.size(), 2);

        getWearUtilMocker().progressClock(Constants.HOUR_IN_MS * 5 + Constants.MINUTE_IN_MS * 30);
        newRaw.addToWatchSet(dataMapForEntries(getWearUtil().timestamp(), 107));
        assertEquals(newRaw.bgDataList.size(), 1);

        getWearUtilMocker().progressClock(Constants.HOUR_IN_MS * 6 + Constants.MINUTE_IN_MS * 30);
        newRaw.addToWatchSet(dataMapForEntries(getWearUtil().timestamp() - Constants.HOUR_IN_MS * 6, 138));
        assertEquals(newRaw.bgDataList.size(), 0);
    }

}

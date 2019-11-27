package info.nightscout.androidaps.data;

import com.google.android.gms.wearable.DataMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;

import info.nightscout.androidaps.interaction.utils.Constants;
import info.nightscout.androidaps.interaction.utils.WearUtil;
import info.nightscout.androidaps.testing.mockers.WearUtilMocker;
import info.nightscout.androidaps.testing.utils.BgWatchDataExt;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(PowerMockRunner.class)
@PrepareForTest( { WearUtil.class } )
public class RawDisplayDataBgEntriesTest {

    @Before
    public void mock() throws Exception {
        WearUtilMocker.prepareMockNoReal();
    }

    //==============================================================================================
    // ENTRIES for chart
    //==============================================================================================

    private DataMap dataMapForEntries() {

        DataMap dataMap = new DataMap();
        ArrayList<DataMap> entries = new ArrayList<>();
        for (int i=0; i<12; i++) {
            DataMap entry = new DataMap();
            entry.putLong("timestamp", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*4*(16-i));
            entry.putDouble("sgvDouble", 145.0-5*i);
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
        RawDisplayData newRaw = new RawDisplayData();
        DataMap multipleEntries = dataMapForEntries();
        DataMap singleEntry1 = dataMapForEntries(WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*4*2,92);
        DataMap singleEntry2 = dataMapForEntries(WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*4*1,88);

        // WHEN, THEN
        // add list
        newRaw.addToWatchSet(multipleEntries);
        assertThat(newRaw.bgDataList.size(), is(12));

        assertThat(new BgWatchDataExt(newRaw.bgDataList.get(5)),
                is(new BgWatchDataExt(new BgWatchData(
                        120.0, 170.0, 80.0,
                        WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*4*(16-5), 0
                ))));

        assertThat(new BgWatchDataExt(newRaw.bgDataList.get(11)),
                is(new BgWatchDataExt(new BgWatchData(
                        90.0, 170.0, 80.0,
                        WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*4*(16-11), 0
                ))));

        // add single entries
        newRaw.addToWatchSet(singleEntry1);
        newRaw.addToWatchSet(singleEntry2);
        assertThat(newRaw.bgDataList.size(), is(14));

        assertThat(new BgWatchDataExt(newRaw.bgDataList.get(12)),
                is(new BgWatchDataExt(new BgWatchData(
                        92.0, 160.0, 90.0,
                        WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*4*2, 1
                ))));
        assertThat(new BgWatchDataExt(newRaw.bgDataList.get(13)),
                is(new BgWatchDataExt(new BgWatchData(
                        88.0, 160.0, 90.0,
                        WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*4*1, 1
                ))));

        // ignore duplicates
        newRaw.addToWatchSet(singleEntry2);
        assertThat(newRaw.bgDataList.size(), is(14));
    }

    @Test
    public void addToWatchSetCleanupOldTest() {
        RawDisplayData newRaw = new RawDisplayData();

        newRaw.addToWatchSet(dataMapForEntries(WearUtil.timestamp(),125));
        assertThat(newRaw.bgDataList.size(), is(1));

        WearUtilMocker.progressClock(Constants.HOUR_IN_MS*2);
        newRaw.addToWatchSet(dataMapForEntries(WearUtil.timestamp(),140));
        assertThat(newRaw.bgDataList.size(), is(2));

        WearUtilMocker.progressClock(Constants.HOUR_IN_MS*1);
        newRaw.addToWatchSet(dataMapForEntries(WearUtil.timestamp(),150));
        WearUtilMocker.progressClock(Constants.HOUR_IN_MS*1 +Constants.MINUTE_IN_MS*30);
        newRaw.addToWatchSet(dataMapForEntries(WearUtil.timestamp(),101));
        assertThat(newRaw.bgDataList.size(), is(4));

        WearUtilMocker.progressClock(Constants.MINUTE_IN_MS*30);
        newRaw.addToWatchSet(dataMapForEntries(WearUtil.timestamp(),90));
        assertThat(newRaw.bgDataList.size(), is(5));

        WearUtilMocker.progressClock(Constants.HOUR_IN_MS*1 +Constants.MINUTE_IN_MS*30);
        newRaw.addToWatchSet(dataMapForEntries(WearUtil.timestamp(),80));
        assertThat(newRaw.bgDataList.size(), is(5));

        WearUtilMocker.progressClock(Constants.HOUR_IN_MS*4);
        newRaw.addToWatchSet(dataMapForEntries(WearUtil.timestamp(),92));
        assertThat(newRaw.bgDataList.size(), is(2));

        WearUtilMocker.progressClock(Constants.HOUR_IN_MS*5 +Constants.MINUTE_IN_MS*30);
        newRaw.addToWatchSet(dataMapForEntries(WearUtil.timestamp(),107));
        assertThat(newRaw.bgDataList.size(), is(1));

        WearUtilMocker.progressClock(Constants.HOUR_IN_MS*6 +Constants.MINUTE_IN_MS*30);
        newRaw.addToWatchSet(dataMapForEntries(WearUtil.timestamp()-Constants.HOUR_IN_MS*6,138));
        assertThat(newRaw.bgDataList.size(), is(0));
    }

}

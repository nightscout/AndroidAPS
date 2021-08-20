package info.nightscout.androidaps.data;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.wearable.DataMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;

import info.nightscout.androidaps.Aaps;
import info.nightscout.androidaps.interaction.utils.Constants;
import info.nightscout.androidaps.interaction.utils.Persistence;
import info.nightscout.androidaps.interaction.utils.WearUtil;
import info.nightscout.androidaps.testing.mockers.AAPSMocker;
import info.nightscout.androidaps.testing.mockers.AndroidMocker;
import info.nightscout.androidaps.testing.mockers.WearUtilMocker;
import info.nightscout.androidaps.testing.mocks.BundleMock;
import info.nightscout.androidaps.testing.mocks.IntentMock;
import info.nightscout.androidaps.testing.utils.BasalWatchDataExt;
import info.nightscout.androidaps.testing.utils.BgWatchDataExt;
import info.nightscout.androidaps.testing.utils.BolusWatchDataExt;
import info.nightscout.androidaps.testing.utils.TempWatchDataExt;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(PowerMockRunner.class)
@PrepareForTest( { WearUtil.class, Log.class, SharedPreferences.class, Context.class, Aaps.class, android.util.Base64.class, Intent.class } )
public class RawDisplayDataBasalsTest {

    @Before
    public void mock() throws Exception {
        AAPSMocker.prepareMock();
        AAPSMocker.resetMockedSharedPrefs();
        AndroidMocker.mockBase64();
        WearUtilMocker.prepareMockNoReal();
    }

    //==============================================================================================
    // BASALS for chart
    //==============================================================================================

    private DataMap dataMapForBasals() {

        DataMap dataMap = new DataMap();

        ArrayList<DataMap> temps = new ArrayList<>();
        DataMap temp = new DataMap();
        temp.putLong("starttime", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*20);
        temp.putDouble("startBasal", 1.5);
        temp.putLong("endtime", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*10);
        temp.putDouble("endbasal", 1.5);
        temp.putDouble("amount", 1.8);
        temps.add(temp);

        DataMap temp2 = new DataMap();
        temp2.putLong("starttime", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*10);
        temp2.putDouble("startBasal", 1.3);
        temp2.putLong("endtime", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*2);
        temp2.putDouble("endbasal", 1.3);
        temp2.putDouble("amount", 2.3);
        temps.add(temp2);
        dataMap.putDataMapArrayList("temps", temps);

        ArrayList<DataMap> basals = new ArrayList<>();
        DataMap basal = new DataMap();
        basal.putLong("starttime", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*20);
        basal.putLong("endtime", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*2);
        basal.putDouble("amount", 1.2);
        basals.add(basal);
        dataMap.putDataMapArrayList("basals", basals);

        ArrayList<DataMap> boluses = new ArrayList<>();
        DataMap bolus = new DataMap();
        bolus.putLong("date", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*17);
        bolus.putDouble("bolus", 5.5);
        bolus.putDouble("carbs", 20.0);
        bolus.putBoolean("isSMB", false);
        bolus.putBoolean("isValid", true);
        boluses.add(bolus);

        DataMap bolus2 = new DataMap();
        bolus2.putLong("date", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*11);
        bolus2.putDouble("bolus", 3.0);
        bolus2.putDouble("carbs", 0.0);
        bolus2.putBoolean("isSMB", false);
        bolus2.putBoolean("isValid", true);
        boluses.add(bolus2);

        DataMap bolus3 = new DataMap();
        bolus3.putLong("date", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*3);
        bolus3.putDouble("bolus", 0.0);
        bolus3.putDouble("carbs", 15.0);
        bolus3.putBoolean("isSMB", true);
        bolus3.putBoolean("isValid", false);
        boluses.add(bolus3);

        dataMap.putDataMapArrayList("boluses", boluses);

        ArrayList<DataMap> predictions = new ArrayList<>();
        for (int i=0; i<10; i++) {
            DataMap prediction = new DataMap();
            prediction.putLong("timestamp", WearUtilMocker.REF_NOW + Constants.MINUTE_IN_MS*i);
            prediction.putDouble("sgv", 160-4*i);
            prediction.putInt("color", 0);
            predictions.add(prediction);
        }
        dataMap.putDataMapArrayList("predictions", predictions);

        return dataMap;
    }

    private void assertBasalsEmpty(RawDisplayData newRaw) {
        assertThat(newRaw.tempWatchDataList.size(), is(0));
        assertThat(newRaw.basalWatchDataList.size(), is(0));
        assertThat(newRaw.bolusWatchDataList.size(), is(0));
        assertThat(newRaw.predictionList.size(), is(0));
    }

    private void assertBasalsOk(RawDisplayData newRaw) {
        assertThat(newRaw.tempWatchDataList.size(), is(2));
        assertThat(newRaw.basalWatchDataList.size(), is(1));
        assertThat(newRaw.bolusWatchDataList.size(), is(3));
        assertThat(newRaw.predictionList.size(), is(10));

        assertThat(new TempWatchDataExt(newRaw.tempWatchDataList.get(0)), is(TempWatchDataExt.build(
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*20,
                1.5,
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*10,
                1.5,
                1.8
        )));

        assertThat(new TempWatchDataExt(newRaw.tempWatchDataList.get(1)), is(TempWatchDataExt.build(
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*10,
                1.3,
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*2,
                1.3,
                2.3
        )));

        assertThat(new BasalWatchDataExt(newRaw.basalWatchDataList.get(0)), is(BasalWatchDataExt.build(
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*20,
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*2,
                1.2
        )));

        assertThat(new BolusWatchDataExt(newRaw.bolusWatchDataList.get(0)), is(BolusWatchDataExt.build(
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*17,
                5.5,
                20,
                false,
                true
        )));

        assertThat(new BolusWatchDataExt(newRaw.bolusWatchDataList.get(1)), is(BolusWatchDataExt.build(
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*11,
                3,
                0,
                false,
                true
        )));

        assertThat(new BolusWatchDataExt(newRaw.bolusWatchDataList.get(2)), is(BolusWatchDataExt.build(
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*3,
                0,
                15,
                true,
                false
        )));


        assertThat(new BgWatchDataExt(newRaw.predictionList.get(3)), is(BgWatchDataExt.build(
                160-4*3,
                WearUtilMocker.REF_NOW + Constants.MINUTE_IN_MS*3,
                0
        )));

        assertThat(new BgWatchDataExt(newRaw.predictionList.get(7)), is(BgWatchDataExt.build(
                160-4*7,
                WearUtilMocker.REF_NOW + Constants.MINUTE_IN_MS*7,
                0
        )));
    }

    @Test
    public void updateBasalsFromEmptyPersistenceTest() {
        // GIVEN
        Persistence persistence = new Persistence();
        RawDisplayData newRaw = new RawDisplayData();

        // WHEN
        newRaw.updateFromPersistence(persistence);

        // THEN
        assertBasalsEmpty(newRaw);
    }

    @Test
    public void updateBasalsFromPersistenceTest() {
        // GIVEN
        Persistence persistence = new Persistence();
        RawDisplayData newRaw = new RawDisplayData();

        // WHEN
        Persistence.storeDataMap(RawDisplayData.BASALS_PERSISTENCE_KEY, dataMapForBasals());
        newRaw.updateFromPersistence(persistence);

        // THEN
        assertBasalsOk(newRaw);
    }

    @Test
    public void partialUpdateBasalsFromPersistenceTest() {
        // GIVEN
        Persistence persistence = new Persistence();
        RawDisplayData newRaw = new RawDisplayData();

        // WHEN
        Persistence.storeDataMap(RawDisplayData.BASALS_PERSISTENCE_KEY, dataMapForBasals());
        newRaw.updateForComplicationsFromPersistence(persistence);

        // THEN
        assertBasalsEmpty(newRaw);
    }

    @Test
    public void updateBasalsFromMessageTest() {
        // GIVEN
        Intent intent = IntentMock.mock();
        Bundle bundle = BundleMock.mock(dataMapForBasals());

        intent.putExtra("basals", bundle);
        RawDisplayData newRaw = new RawDisplayData();

        // WHEN
        newRaw.updateBasalsFromMessage(intent, null);

        // THEN
        assertBasalsOk(newRaw);
    }

    @Test
    public void updateBasalsFromEmptyMessageTest() {
        // GIVEN
        Intent intent = IntentMock.mock();
        RawDisplayData newRaw = new RawDisplayData();

        // WHEN
        newRaw.updateBasalsFromMessage(intent, null);

        // THEN
        assertBasalsEmpty(newRaw);
    }

}

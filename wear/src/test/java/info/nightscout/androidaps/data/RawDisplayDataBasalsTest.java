package info.nightscout.androidaps.data;

import static org.junit.Assert.assertEquals;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.wearable.DataMap;

import org.junit.Test;

import java.util.ArrayList;

import info.nightscout.androidaps.TestBase;
import info.nightscout.androidaps.interaction.utils.Constants;
import info.nightscout.androidaps.testing.mockers.WearUtilMocker;
import info.nightscout.androidaps.testing.mocks.BundleMock;
import info.nightscout.androidaps.testing.mocks.IntentMock;
import info.nightscout.androidaps.testing.utils.BasalWatchDataExt;
import info.nightscout.androidaps.testing.utils.BgWatchDataExt;
import info.nightscout.androidaps.testing.utils.BolusWatchDataExt;
import info.nightscout.androidaps.testing.utils.TempWatchDataExt;

@SuppressWarnings("SpellCheckingInspection")
public class RawDisplayDataBasalsTest extends TestBase {


    //==============================================================================================
    // BASALS for chart
    //==============================================================================================

    private DataMap dataMapForBasals() {

        DataMap dataMap = new DataMap();

        ArrayList<DataMap> temps = new ArrayList<>();
        DataMap temp = new DataMap();
        temp.putLong("starttime", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 20);
        temp.putDouble("startBasal", 1.5);
        temp.putLong("endtime", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 10);
        temp.putDouble("endbasal", 1.5);
        temp.putDouble("amount", 1.8);
        temps.add(temp);

        DataMap temp2 = new DataMap();
        temp2.putLong("starttime", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 10);
        temp2.putDouble("startBasal", 1.3);
        temp2.putLong("endtime", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 2);
        temp2.putDouble("endbasal", 1.3);
        temp2.putDouble("amount", 2.3);
        temps.add(temp2);
        dataMap.putDataMapArrayList("temps", temps);

        ArrayList<DataMap> basals = new ArrayList<>();
        DataMap basal = new DataMap();
        basal.putLong("starttime", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 20);
        basal.putLong("endtime", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 2);
        basal.putDouble("amount", 1.2);
        basals.add(basal);
        dataMap.putDataMapArrayList("basals", basals);

        ArrayList<DataMap> boluses = new ArrayList<>();
        DataMap bolus = new DataMap();
        bolus.putLong("date", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 17);
        bolus.putDouble("bolus", 5.5);
        bolus.putDouble("carbs", 20.0);
        bolus.putBoolean("isSMB", false);
        bolus.putBoolean("isValid", true);
        boluses.add(bolus);

        DataMap bolus2 = new DataMap();
        bolus2.putLong("date", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 11);
        bolus2.putDouble("bolus", 3.0);
        bolus2.putDouble("carbs", 0.0);
        bolus2.putBoolean("isSMB", false);
        bolus2.putBoolean("isValid", true);
        boluses.add(bolus2);

        DataMap bolus3 = new DataMap();
        bolus3.putLong("date", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 3);
        bolus3.putDouble("bolus", 0.0);
        bolus3.putDouble("carbs", 15.0);
        bolus3.putBoolean("isSMB", true);
        bolus3.putBoolean("isValid", false);
        boluses.add(bolus3);

        dataMap.putDataMapArrayList("boluses", boluses);

        ArrayList<DataMap> predictions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            DataMap prediction = new DataMap();
            prediction.putLong("timestamp", WearUtilMocker.REF_NOW + Constants.MINUTE_IN_MS * i);
            prediction.putDouble("sgv", 160 - 4 * i);
            prediction.putInt("color", 0);
            predictions.add(prediction);
        }
        dataMap.putDataMapArrayList("predictions", predictions);

        return dataMap;
    }

    private void assertBasalsEmpty(RawDisplayData newRaw) {
        assertEquals(newRaw.tempWatchDataList.size(), 0);
        assertEquals(newRaw.basalWatchDataList.size(), 0);
        assertEquals(newRaw.bolusWatchDataList.size(), 0);
        assertEquals(newRaw.predictionList.size(), 0);
    }

    private void assertBasalsOk(RawDisplayData newRaw) {
        assertEquals(newRaw.tempWatchDataList.size(), 2);
        assertEquals(newRaw.basalWatchDataList.size(), 1);
        assertEquals(newRaw.bolusWatchDataList.size(), 3);
        assertEquals(newRaw.predictionList.size(), 10);

        assertEquals(new TempWatchDataExt(newRaw.tempWatchDataList.get(0)), TempWatchDataExt.build(
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 20,
                1.5,
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 10,
                1.5,
                1.8
        ));

        assertEquals(new TempWatchDataExt(newRaw.tempWatchDataList.get(1)), TempWatchDataExt.build(
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 10,
                1.3,
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 2,
                1.3,
                2.3
        ));

        assertEquals(new BasalWatchDataExt(newRaw.basalWatchDataList.get(0)), BasalWatchDataExt.build(
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 20,
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 2,
                1.2
        ));

        assertEquals(new BolusWatchDataExt(newRaw.bolusWatchDataList.get(0)), BolusWatchDataExt.build(
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 17,
                5.5,
                20,
                false,
                true
        ));

        assertEquals(new BolusWatchDataExt(newRaw.bolusWatchDataList.get(1)), BolusWatchDataExt.build(
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 11,
                3,
                0,
                false,
                true
        ));

        assertEquals(new BolusWatchDataExt(newRaw.bolusWatchDataList.get(2)), BolusWatchDataExt.build(
                WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 3,
                0,
                15,
                true,
                false
        ));


        assertEquals(new BgWatchDataExt(newRaw.predictionList.get(3)), BgWatchDataExt.build(
                160 - 4 * 3,
                WearUtilMocker.REF_NOW + Constants.MINUTE_IN_MS * 3,
                0
        ));

        assertEquals(new BgWatchDataExt(newRaw.predictionList.get(7)), BgWatchDataExt.build(
                160 - 4 * 7,
                WearUtilMocker.REF_NOW + Constants.MINUTE_IN_MS * 7,
                0
        ));
    }

    @Test
    public void updateBasalsFromEmptyPersistenceTest() {
        // GIVEN
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        // WHEN
        newRaw.updateFromPersistence(persistence);

        // THEN
        assertBasalsEmpty(newRaw);
    }

    @Test
    public void updateBasalsFromPersistenceTest() {
        // GIVEN
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        // WHEN
        persistence.storeDataMap(RawDisplayData.BASALS_PERSISTENCE_KEY, dataMapForBasals());
        newRaw.updateFromPersistence(persistence);

        // THEN
        assertBasalsOk(newRaw);
    }

    @Test
    public void partialUpdateBasalsFromPersistenceTest() {
        // GIVEN
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        // WHEN
        persistence.storeDataMap(RawDisplayData.BASALS_PERSISTENCE_KEY, dataMapForBasals());
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
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        // WHEN
        newRaw.updateBasalsFromMessage(intent, null);

        // THEN
        assertBasalsOk(newRaw);
    }

    @Test
    public void updateBasalsFromEmptyMessageTest() {
        // GIVEN
        Intent intent = IntentMock.mock();
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        // WHEN
        newRaw.updateBasalsFromMessage(intent, null);

        // THEN
        assertBasalsEmpty(newRaw);
    }

}

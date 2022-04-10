package info.nightscout.androidaps.data;

import static org.junit.Assert.assertEquals;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.wearable.DataMap;

import org.junit.Test;

import info.nightscout.androidaps.TestBase;
import info.nightscout.androidaps.interaction.utils.Constants;
import info.nightscout.androidaps.testing.mockers.WearUtilMocker;
import info.nightscout.androidaps.testing.mocks.BundleMock;
import info.nightscout.androidaps.testing.mocks.IntentMock;

public class RawDataSgvDisplayDataTest extends TestBase {

    //==============================================================================================
    // SGV DATA
    //==============================================================================================

    private DataMap dataMapForData() {
        DataMap dataMap = new DataMap();
        dataMap.putLong("sgvLevel", 1L);
        dataMap.putLong("timestamp", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS);
        dataMap.putString("sgvString", "106");
        dataMap.putString("slopeArrow", "↗");
        dataMap.putString("delta", "5.4");
        dataMap.putString("avgDelta", "3.7");
        dataMap.putString("glucoseUnits", "mg/dl");
        return dataMap;
    }

    private void assertDataEmpty(RawDisplayData newRaw) {
        assertEquals(newRaw.sgvLevel, 0L);
        assertEquals(newRaw.datetime, 0L);
        assertEquals(newRaw.sSgv, "---");
        assertEquals(newRaw.sDirection, "--");
        assertEquals(newRaw.sDelta, "--");
        assertEquals(newRaw.sAvgDelta, "--");
        assertEquals(newRaw.sUnits, "-");
    }

    private void assertDataOk(RawDisplayData newRaw) {
        assertEquals(newRaw.sgvLevel, 1L);
        assertEquals(newRaw.datetime, WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS);
        assertEquals(newRaw.sSgv, "106");
        assertEquals(newRaw.sDirection, "↗");
        assertEquals(newRaw.sDelta, "5.4");
        assertEquals(newRaw.sAvgDelta, "3.7");
        assertEquals(newRaw.sUnits, "mg/dl");
    }

    @Test
    public void updateDataFromEmptyPersistenceTest() {
        // GIVEN
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        // WHEN
        newRaw.updateFromPersistence(persistence);

        // THEN
        assertDataEmpty(newRaw);
    }

    @Test
    public void updateDataFromPersistenceTest() {
        // GIVEN
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        // WHEN
        persistence.storeDataMap(RawDisplayData.DATA_PERSISTENCE_KEY, dataMapForData());
        newRaw.updateFromPersistence(persistence);

        // THEN
        assertDataOk(newRaw);
    }

    @Test
    public void partialUpdateDataFromPersistenceTest() {
        // GIVEN
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        // WHEN
        persistence.storeDataMap(RawDisplayData.DATA_PERSISTENCE_KEY, dataMapForData());
        newRaw.updateForComplicationsFromPersistence(persistence);

        // THEN
        assertDataOk(newRaw);
    }

    @Test
    public void updateDataFromMessageTest() {
        // GIVEN
        Intent intent = IntentMock.mock();
        Bundle bundle = BundleMock.mock(dataMapForData());

        intent.putExtra("data", bundle);
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        // WHEN
        newRaw.updateDataFromMessage(intent, null);

        // THEN
        assertDataOk(newRaw);
    }

    @Test
    public void updateDataFromEmptyMessageTest() {
        // GIVEN
        Intent intent = IntentMock.mock();
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        // WHEN
        newRaw.updateDataFromMessage(intent, null);

        // THEN
        assertDataEmpty(newRaw);
    }

}

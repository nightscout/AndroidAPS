package info.nightscout.androidaps.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.wearable.DataMap;

import org.junit.Before;
import org.junit.Test;

import info.nightscout.androidaps.TestBase;
import info.nightscout.androidaps.interaction.utils.Constants;
import info.nightscout.androidaps.testing.mockers.RawDataMocker;
import info.nightscout.androidaps.testing.mockers.WearUtilMocker;
import info.nightscout.androidaps.testing.mocks.BundleMock;
import info.nightscout.androidaps.testing.mocks.IntentMock;

@SuppressWarnings("SimplifiableAssertion")
public class RawDisplayDataStatusTest extends TestBase {

    private RawDataMocker rawDataMocker;

    @Before
    public void mock() {
        rawDataMocker = new RawDataMocker(getWearUtil());
    }

    @SuppressWarnings("AssertBetweenInconvertibleTypes") @Test
    public void toDebugStringTest() {
        RawDisplayData raw = rawDataMocker.rawDelta(5, "1.5");
        raw.externalStatusString = "placeholder-here";

        assertEquals(raw.datetime, WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 5);
        assertTrue(raw.toDebugString().contains("placeholder-here"));
    }

    //==============================================================================================
    // STATUS
    //==============================================================================================

    private DataMap dataMapForStatus() {
        DataMap dataMap = new DataMap();
        dataMap.putString("currentBasal", "120%");
        dataMap.putString("battery", "76");
        dataMap.putString("rigBattery", "40%");
        dataMap.putBoolean("detailedIob", true);
        dataMap.putString("iobSum", "12.5");
        dataMap.putString("iobDetail", "(11,2|1,3)");
        dataMap.putString("cob", "5(10)g");
        dataMap.putString("bgi", "13");
        dataMap.putBoolean("showBgi", false);
        dataMap.putString("externalStatusString", "");
        dataMap.putInt("batteryLevel", 1);
        dataMap.putLong("openApsStatus", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 2);
        return dataMap;
    }

    private void assertStatusEmpty(RawDisplayData newRaw) {
        assertEquals(newRaw.sBasalRate, "-.--U/h");
        assertEquals(newRaw.sUploaderBattery, "--");
        assertEquals(newRaw.sRigBattery, "--");
        assertEquals(newRaw.detailedIOB, false);
        assertEquals(newRaw.sIOB1, "IOB");
        assertEquals(newRaw.sIOB2, "-.--");
        assertEquals(newRaw.sCOB1, "Carb");
        assertEquals(newRaw.sCOB2, "--g");
        assertEquals(newRaw.sBgi, "--");
        assertEquals(newRaw.showBGI, false);
        assertEquals(newRaw.externalStatusString, "no status");
        assertEquals(newRaw.batteryLevel, 1);
        assertEquals(newRaw.openApsStatus, -1L);
    }

    private void assertStatusOk(RawDisplayData newRaw) {
        assertEquals(newRaw.sBasalRate, "120%");
        assertEquals(newRaw.sUploaderBattery, "76");
        assertEquals(newRaw.sRigBattery, "40%");
        assertEquals(newRaw.detailedIOB, true);
        assertEquals(newRaw.sIOB1, "12.5U");
        assertEquals(newRaw.sIOB2, "(11,2|1,3)");
        assertEquals(newRaw.sCOB1, "Carb");
        assertEquals(newRaw.sCOB2, "5(10)g");
        assertEquals(newRaw.sBgi, "13");
        assertEquals(newRaw.showBGI, false);
        assertEquals(newRaw.externalStatusString, "");
        assertEquals(newRaw.batteryLevel, 1);
        assertEquals(newRaw.openApsStatus, WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS * 2);
    }

    @Test
    public void updateStatusFromEmptyPersistenceTest() {
        // GIVEN
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        // WHEN
        newRaw.updateFromPersistence(persistence);

        // THEN
        assertStatusEmpty(newRaw);
    }

    @Test
    public void updateStatusFromPersistenceTest() {
        // GIVEN
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        // WHEN
        persistence.storeDataMap(RawDisplayData.STATUS_PERSISTENCE_KEY, dataMapForStatus());
        newRaw.updateFromPersistence(persistence);

        // THEN
        assertStatusOk(newRaw);
    }

    @Test
    public void partialUpdateStatusFromPersistenceTest() {
        // GIVEN
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        // WHEN
        persistence.storeDataMap(RawDisplayData.STATUS_PERSISTENCE_KEY, dataMapForStatus());
        newRaw.updateForComplicationsFromPersistence(persistence);

        // THEN
        assertStatusOk(newRaw);
    }

    @Test
    public void updateStatusFromMessageTest() {
        // GIVEN
        Intent intent = IntentMock.mock();
        Bundle bundle = BundleMock.mock(dataMapForStatus());

        intent.putExtra("status", bundle);
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        // WHEN
        newRaw.updateStatusFromMessage(intent, null);

        // THEN
        assertStatusOk(newRaw);
    }

    @Test
    public void updateStatusFromEmptyMessageTest() {
        // GIVEN
        Intent intent = IntentMock.mock();
        RawDisplayData newRaw = new RawDisplayData(getWearUtil());

        // WHEN
        newRaw.updateStatusFromMessage(intent, null);

        // THEN
        assertStatusEmpty(newRaw);
    }

}

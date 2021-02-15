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

import info.nightscout.androidaps.Aaps;
import info.nightscout.androidaps.interaction.utils.Constants;
import info.nightscout.androidaps.interaction.utils.Persistence;
import info.nightscout.androidaps.interaction.utils.WearUtil;
import info.nightscout.androidaps.testing.mockers.AAPSMocker;
import info.nightscout.androidaps.testing.mockers.AndroidMocker;
import info.nightscout.androidaps.testing.mockers.WearUtilMocker;
import info.nightscout.androidaps.testing.mocks.BundleMock;
import info.nightscout.androidaps.testing.mocks.IntentMock;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(PowerMockRunner.class)
@PrepareForTest( { WearUtil.class, Log.class, SharedPreferences.class, Context.class, Aaps.class, android.util.Base64.class, Intent.class } )
public class RawDataSgvDisplayDataTest {

    @Before
    public void mock() throws Exception {
        AAPSMocker.prepareMock();
        AAPSMocker.resetMockedSharedPrefs();
        AndroidMocker.mockBase64();
        WearUtilMocker.prepareMockNoReal();
    }

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
        assertThat(newRaw.sgvLevel, is(0L));
        assertThat(newRaw.datetime, is(0L));
        assertThat(newRaw.sSgv, is("---"));
        assertThat(newRaw.sDirection, is("--"));
        assertThat(newRaw.sDelta, is("--"));
        assertThat(newRaw.sAvgDelta, is("--"));
        assertThat(newRaw.sUnits, is("-"));
    }

    private void assertDataOk(RawDisplayData newRaw) {
        assertThat(newRaw.sgvLevel, is(1L));
        assertThat(newRaw.datetime, is(WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS));
        assertThat(newRaw.sSgv, is("106"));
        assertThat(newRaw.sDirection, is("↗"));
        assertThat(newRaw.sDelta, is("5.4"));
        assertThat(newRaw.sAvgDelta, is("3.7"));
        assertThat(newRaw.sUnits, is("mg/dl"));
    }

    @Test
    public void updateDataFromEmptyPersistenceTest() {
        // GIVEN
        Persistence persistence = new Persistence();
        RawDisplayData newRaw = new RawDisplayData();

        // WHEN
        newRaw.updateFromPersistence(persistence);

        // THEN
        assertDataEmpty(newRaw);
    }

    @Test
    public void updateDataFromPersistenceTest() {
        // GIVEN
        Persistence persistence = new Persistence();
        RawDisplayData newRaw = new RawDisplayData();

        // WHEN
        Persistence.storeDataMap(RawDisplayData.DATA_PERSISTENCE_KEY, dataMapForData());
        newRaw.updateFromPersistence(persistence);

        // THEN
        assertDataOk(newRaw);
    }

    @Test
    public void partialUpdateDataFromPersistenceTest() {
        // GIVEN
        Persistence persistence = new Persistence();
        RawDisplayData newRaw = new RawDisplayData();

        // WHEN
        Persistence.storeDataMap(RawDisplayData.DATA_PERSISTENCE_KEY, dataMapForData());
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
        RawDisplayData newRaw = new RawDisplayData();

        // WHEN
        newRaw.updateDataFromMessage(intent, null);

        // THEN
        assertDataOk(newRaw);
    }

    @Test
    public void updateDataFromEmptyMessageTest() {
        // GIVEN
        Intent intent = IntentMock.mock();
        RawDisplayData newRaw = new RawDisplayData();

        // WHEN
        newRaw.updateDataFromMessage(intent, null);

        // THEN
        assertDataEmpty(newRaw);
    }

}

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

import info.nightscout.androidaps.aaps;
import info.nightscout.androidaps.interaction.utils.Constants;
import info.nightscout.androidaps.interaction.utils.Persistence;
import info.nightscout.androidaps.interaction.utils.WearUtil;
import info.nightscout.androidaps.testing.mockers.AAPSMocker;
import info.nightscout.androidaps.testing.mockers.AndroidMocker;
import info.nightscout.androidaps.testing.mockers.RawDataMocker;
import info.nightscout.androidaps.testing.mockers.WearUtilMocker;
import info.nightscout.androidaps.testing.mocks.BundleMock;
import info.nightscout.androidaps.testing.mocks.IntentMock;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(PowerMockRunner.class)
@PrepareForTest( { WearUtil.class, Log.class, SharedPreferences.class, Context.class, aaps.class, android.util.Base64.class, Intent.class } )
public class RawDisplayDataStatusTest {

    @Before
    public void mock() throws Exception {
        AAPSMocker.prepareMock();
        AAPSMocker.resetMockedSharedPrefs();
        AndroidMocker.mockBase64();
        WearUtilMocker.prepareMockNoReal();
    }

    @Test
    public void toDebugStringTest() {
        RawDisplayData raw = RawDataMocker.rawDelta(5, "1.5");
        raw.externalStatusString = "placeholder-here";

        assertThat(raw.datetime, is(WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*5));
        assertThat(raw.toDebugString(), containsString("placeholder-here"));
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
        dataMap.putString("iobSum", "12.5") ;
        dataMap.putString("iobDetail","(11,2|1,3)");
        dataMap.putString("cob","5(10)g");
        dataMap.putString("bgi", "13");
        dataMap.putBoolean("showBgi", false);
        dataMap.putString("externalStatusString", "");
        dataMap.putInt("batteryLevel", 1);
        dataMap.putLong("openApsStatus", WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*2);
        return dataMap;
    }

    private void assertStatusEmpty(RawDisplayData newRaw) {
        assertThat(newRaw.sBasalRate, is("-.--U/h"));
        assertThat(newRaw.sUploaderBattery, is("--"));
        assertThat(newRaw.sRigBattery, is("--"));
        assertThat(newRaw.detailedIOB, is(false));
        assertThat(newRaw.sIOB1, is("IOB"));
        assertThat(newRaw.sIOB2, is("-.--"));
        assertThat(newRaw.sCOB1, is("Carb"));
        assertThat(newRaw.sCOB2, is("--g"));
        assertThat(newRaw.sBgi, is("--"));
        assertThat(newRaw.showBGI, is(false));
        assertThat(newRaw.externalStatusString, is("no status"));
        assertThat(newRaw.batteryLevel, is(1));
        assertThat(newRaw.openApsStatus, is(-1L));
    }

    private void assertStatusOk(RawDisplayData newRaw) {
        assertThat(newRaw.sBasalRate, is("120%"));
        assertThat(newRaw.sUploaderBattery, is("76"));
        assertThat(newRaw.sRigBattery, is("40%"));
        assertThat(newRaw.detailedIOB, is(true));
        assertThat(newRaw.sIOB1, is("12.5U"));
        assertThat(newRaw.sIOB2, is("(11,2|1,3)"));
        assertThat(newRaw.sCOB1, is("Carb"));
        assertThat(newRaw.sCOB2, is("5(10)g"));
        assertThat(newRaw.sBgi, is("13"));
        assertThat(newRaw.showBGI, is(false));
        assertThat(newRaw.externalStatusString, is(""));
        assertThat(newRaw.batteryLevel, is(1));
        assertThat(newRaw.openApsStatus, is(WearUtilMocker.REF_NOW - Constants.MINUTE_IN_MS*2));
    }

    @Test
    public void updateStatusFromEmptyPersistenceTest() {
        // GIVEN
        Persistence persistence = new Persistence();
        RawDisplayData newRaw = new RawDisplayData();

        // WHEN
        newRaw.updateFromPersistence(persistence);

        // THEN
        assertStatusEmpty(newRaw);
    }

    @Test
    public void updateStatusFromPersistenceTest() {
        // GIVEN
        Persistence persistence = new Persistence();
        RawDisplayData newRaw = new RawDisplayData();

        // WHEN
        Persistence.storeDataMap(RawDisplayData.STATUS_PERSISTENCE_KEY, dataMapForStatus());
        newRaw.updateFromPersistence(persistence);

        // THEN
        assertStatusOk(newRaw);
    }

    @Test
    public void partialUpdateStatusFromPersistenceTest() {
        // GIVEN
        Persistence persistence = new Persistence();
        RawDisplayData newRaw = new RawDisplayData();

        // WHEN
        Persistence.storeDataMap(RawDisplayData.STATUS_PERSISTENCE_KEY, dataMapForStatus());
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
        RawDisplayData newRaw = new RawDisplayData();

        // WHEN
        newRaw.updateStatusFromMessage(intent, null);

        // THEN
        assertStatusOk(newRaw);
    }

    @Test
    public void updateStatusFromEmptyMessageTest() {
        // GIVEN
        Intent intent = IntentMock.mock();
        RawDisplayData newRaw = new RawDisplayData();

        // WHEN
        newRaw.updateStatusFromMessage(intent, null);

        // THEN
        assertStatusEmpty(newRaw);
    }

}

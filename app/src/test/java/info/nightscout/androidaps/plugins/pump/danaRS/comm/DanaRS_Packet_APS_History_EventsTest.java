package info.nightscout.androidaps.plugins.pump.danaRS.comm;


import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Calendar;
import java.util.GregorianCalendar;

import info.AAPSMocker;
import info.SPMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.treatments.TreatmentService;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.assertEquals;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Created by Rumen on 31.07.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({SP.class, MainApp.class, ConfigBuilderPlugin.class, Context.class, NSUpload.class, TreatmentsPlugin.class, TreatmentService.class, DatabaseHelper.class, DateUtil.class})
public class DanaRS_Packet_APS_History_EventsTest extends DanaRS_Packet_APS_History_Events {

    @Test
    public void runTest() {
        DanaRS_Packet_APS_History_Events testPacket = new DanaRS_Packet_APS_History_Events(DateUtil.now());
        // test getRequestedParams
        byte[] returnedValues = testPacket.getRequestParams();
        byte[] expectedValues = getCalender(DateUtil.now());
        //year
        assertEquals(expectedValues[0], returnedValues[0]);
        //month
        assertEquals(expectedValues[1], returnedValues[1]);
        //day of month
        assertEquals(expectedValues[2], returnedValues[2]);
        // hour
        assertEquals(expectedValues[3], returnedValues[3]);
        // minute
        assertEquals(expectedValues[4], returnedValues[4]);
        // second
        assertEquals(expectedValues[5], returnedValues[5]);

        // test message decoding
        testPacket.handleMessage(createArray(50, (byte) 0));
        assertEquals(false, failed);
//        testPacket.handleMessage(createArray(50, (byte) 1));
//        assertEquals(true, done);

        assertEquals("APS_HISTORY_EVENTS", getFriendlyName());
    }

    byte[] createArray(int length, byte fillWith) {
        byte[] ret = new byte[length];
        for (int i = 0; i < length; i++) {
            ret[i] = fillWith;
        }
        return ret;
    }

    public byte[] getCalender(long from) {
        GregorianCalendar cal = new GregorianCalendar();
        if (from != 0)
            cal.setTimeInMillis(from);
        else
            cal.set(2000, 0, 1, 0, 0, 0);
        byte[] ret = new byte[6];
        ret[0] = (byte) ((cal.get(Calendar.YEAR) - 1900 - 100) & 0xff);
        ret[1] = (byte) ((cal.get(Calendar.MONTH) + 1) & 0xff);
        ret[2] = (byte) ((cal.get(Calendar.DAY_OF_MONTH)) & 0xff);
        ret[3] = (byte) ((cal.get(Calendar.HOUR_OF_DAY)) & 0xff);
        ret[4] = (byte) ((cal.get(Calendar.MINUTE)) & 0xff);
        ret[5] = (byte) ((cal.get(Calendar.SECOND)) & 0xff);

        return ret;
    }

    @Before
    public void prepareTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        SPMocker.prepareMock();
        SP.putString("profile", AAPSMocker.getValidProfileStore().getData().toString());
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockStrings();
        PowerMockito.mockStatic(NSUpload.class);
        AAPSMocker.mockDatabaseHelper();

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(5456465445L);
    }
}

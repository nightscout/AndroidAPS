package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import static org.mockito.Mockito.when;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.SPMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;

/**
 * Created by andy on 6/16/18.
 */
//@RunWith(PowerMockRunner.class)
//@PrepareForTest({ MainApp.class, DatabaseHelper.class, DateUtil.class, SP.class })
@Ignore
public class BasalProfileUTest {

    // MainApp mainApp = new MainApp();
    @Before
    public void initMocking() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockStrings();
        AAPSMocker.mockDatabaseHelper();

        SPMocker.prepareMock();

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(1514766900000L + T.mins(1).msecs());
    }


    @Test
    public void getProfilesByHour() throws Exception {

        MedtronicUtil.setPumpStatus(new MedtronicPumpStatus(new PumpDescription()));
        MedtronicUtil.getPumpStatus().pumpType = PumpType.Medtronic_522_722;

        PumpType pumpType = MedtronicUtil.getPumpStatus().pumpType;

        BasalProfile basalProfile = new BasalProfile();
        byte[] data = { //
        0x48, 0x00, 0x00, 0x40, 0x00, 0x02, 0x38, 0x00, 0x04, 0x3A, 0x00, 0x06, 0x32, 0x00, 0x0C, 0x26, 0x00, //
        0x10, 0x2E, 0x00, 0x14, 0x32, 0x00, 0x18, 0x26, 0x00, 0x1A, 0x1A, 0x00, 0x20, 0x14, 0x00, 0x2A, 0x00, //
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };

        basalProfile.setRawData(data);

        Double[] profilesByHour = basalProfile.getProfilesByHour();

        Assert.assertTrue(MedtronicUtil.isSame(1.8d, profilesByHour[0]));
        Assert.assertTrue(MedtronicUtil.isSame(1.6d, profilesByHour[1]));
        Assert.assertTrue(MedtronicUtil.isSame(1.4d, profilesByHour[2]));
        Assert.assertTrue(MedtronicUtil.isSame(1.45d, profilesByHour[3]));
        Assert.assertTrue(MedtronicUtil.isSame(1.45d, profilesByHour[4]));
        Assert.assertTrue(MedtronicUtil.isSame(1.45d, profilesByHour[5]));
        Assert.assertTrue(MedtronicUtil.isSame(1.25d, profilesByHour[6]));
        Assert.assertTrue(MedtronicUtil.isSame(1.25d, profilesByHour[7]));
        Assert.assertTrue(MedtronicUtil.isSame(0.95d, profilesByHour[8]));
        Assert.assertTrue(MedtronicUtil.isSame(0.95d, profilesByHour[9]));
        Assert.assertTrue(MedtronicUtil.isSame(1.15d, profilesByHour[10]));
        Assert.assertTrue(MedtronicUtil.isSame(1.15d, profilesByHour[11]));
        Assert.assertTrue(MedtronicUtil.isSame(1.25d, profilesByHour[12]));
        Assert.assertTrue(MedtronicUtil.isSame(0.95d, profilesByHour[13]));
        Assert.assertTrue(MedtronicUtil.isSame(0.95d, profilesByHour[14]));
        Assert.assertTrue(MedtronicUtil.isSame(0.95d, profilesByHour[15]));
        Assert.assertTrue(MedtronicUtil.isSame(0.65d, profilesByHour[16]));
        Assert.assertTrue(MedtronicUtil.isSame(0.65d, profilesByHour[17]));
        Assert.assertTrue(MedtronicUtil.isSame(0.65d, profilesByHour[18]));
        Assert.assertTrue(MedtronicUtil.isSame(0.65d, profilesByHour[19]));
        Assert.assertTrue(MedtronicUtil.isSame(0.65d, profilesByHour[20]));
        Assert.assertTrue(MedtronicUtil.isSame(0.5d, profilesByHour[21]));
        Assert.assertTrue(MedtronicUtil.isSame(0.5d, profilesByHour[22]));
        Assert.assertTrue(MedtronicUtil.isSame(0.5d, profilesByHour[23]));

        System.out.println("Basals by hour: "
            + (profilesByHour == null ? "null" : BasalProfile.getProfilesByHourToString(profilesByHour)));
    }


    @Test
    public void testProfileByDay2() {
        BasalProfile basalProfile = new BasalProfile();
        byte[] data = { //
        0x32, 0x00, 0x00, 0x2C, 0x00, 0x0C, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };

        basalProfile.setRawData(data);

        Double[] profilesByHour = basalProfile.getProfilesByHour();

        System.out.println("Basals by hour: "
            + (profilesByHour == null ? "null" : BasalProfile.getProfilesByHourToString(profilesByHour)));

    }


    @Test
    public void testProfileByDayZero() {
        BasalProfile basalProfile = new BasalProfile();
        byte[] data = { //
        0x00 };

        basalProfile.setRawData(data);

        Double[] profilesByHour = basalProfile.getProfilesByHour();

        System.out.println("Basals by hour: "
            + (profilesByHour == null ? "null" : BasalProfile.getProfilesByHourToString(profilesByHour)));

    }


    @Test
    public void testProfileByDayZeroZero3F() {
        BasalProfile basalProfile = new BasalProfile();
        byte[] data = { //
        0x00, 0x00, 0x3f };

        basalProfile.setRawData(data);

        Double[] profilesByHour = basalProfile.getProfilesByHour();

        System.out.println("Basals by hour: "
            + (profilesByHour == null ? "null" : BasalProfile.getProfilesByHourToString(profilesByHour)));

    }


    @Test
    public void testProfileFromHistory() {
        MedtronicUtil.setPumpStatus(new MedtronicPumpStatus(new PumpDescription()));
        MedtronicUtil.getPumpStatus().pumpType = PumpType.Medtronic_522_722;

        byte[] data = {
            0, 72, 0, 2, 64, 0, 4, 56, 0, 6, 58, 0, 8, 58, 0, 10, 58, 0, 12, 50, 0, 14, 50, 0, 16, 38, 0, 18, 38, 0,
            20, 46, 0, 22, 46, 0, 24, 50, 0, 26, 38, 0, 28, 38, 0, 30, 38, 0, 32, 26, 0, 34, 26, 0, 36, 26, 0, 38, 26,
            0, 40, 26, 0, 42, 20, 0, 44, 20, 0, 46, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

        BasalProfile basalProfile = new BasalProfile();
        basalProfile.setRawDataFromHistory(data);

        Double[] profilesByHour = basalProfile.getProfilesByHour();

        System.out.println("Basals by hour: "
            + (profilesByHour == null ? "null" : BasalProfile.getProfilesByHourToString(profilesByHour)));

        // 1.800 1.600 1.400 1.450 1.450 1.450 1.250 1.250 0.950 0.950 1.150 1.150 1.250 0.950 0.950 0.950 0.650 0.650
        // 0.650 0.650 0.650 0.500 0.500 0.500
    }

}

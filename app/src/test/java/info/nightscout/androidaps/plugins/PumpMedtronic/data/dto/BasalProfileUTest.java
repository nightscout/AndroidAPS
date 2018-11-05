package info.nightscout.androidaps.plugins.PumpMedtronic.data.dto;

import static org.powermock.api.mockito.PowerMockito.when;

import junit.framework.Assert;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
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
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpType;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.SP;
import info.nightscout.utils.T;

/**
 * Created by andy on 6/16/18.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ MainApp.class, DatabaseHelper.class, DateUtil.class, SP.class })
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
            + (profilesByHour == null ? "null" : StringUtils.join(profilesByHour, " ")));

    }

}

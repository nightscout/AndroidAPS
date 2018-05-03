package info.nightscout.utils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpMDI.MDIPlugin;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by kuchjir on 12/12/2017.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( { MainApp.class, GlucoseStatus.class, ConfigBuilderPlugin.class, TreatmentsPlugin.class })
public class BolusWizardTest {
    private static final double PUMP_BOLUS_STEP = 0.1;

    @Test
    /** Should calculate the same bolus when different blood glucose but both in target range */
    public void shuldCalculateTheSameBolusWhenBGsInRange() throws Exception {
        BolusWizard bw = new BolusWizard();
        Profile profile = setupProfile(4d, 8d, 20d, 12d);

        Double bolusForBg42 = bw.doCalc(profile, null, 20, 0.0,4.2, 0d, 100d, true, true, false, false);
        Double bolusForBg54 = bw.doCalc(profile, null, 20, 0.0,5.4, 0d, 100d, true, true, false, false);
        Assert.assertEquals(bolusForBg42, bolusForBg54);
    }

    @Test
    public void shuldCalculateHigherBolusWhenHighBG() throws Exception {
        BolusWizard bw = new BolusWizard();
        Profile profile = setupProfile(4d, 8d, 20d, 12d);

        Double bolusForHighBg = bw.doCalc(profile, null, 20, 0d,9.8, 0d, 100d, true, true, false, false);
        Double bolusForBgInRange = bw.doCalc(profile, null, 20, 0.0,5.4, 0d, 100d, true, true, false, false);
        Assert.assertTrue(bolusForHighBg > bolusForBgInRange);
    }

    @Test
    public void shuldCalculateLowerBolusWhenLowBG() throws Exception {
        BolusWizard bw = new BolusWizard();
        Profile profile = setupProfile(4d, 8d, 20d, 12d);

        Double bolusForLowBg = bw.doCalc(profile, null, 20, 0d,3.6, 0d, 100d, true, true, false, false);
        Double bolusForBgInRange = bw.doCalc(profile, null, 20, 0.0,5.4, 0d, 100d, true, true, false, false);
        Assert.assertTrue(bolusForLowBg < bolusForBgInRange);
    }

    private Profile setupProfile(Double targetLow, Double targetHigh, Double insulinSensitivityFactor, Double insulinToCarbRatio) {
        Profile profile = mock(Profile.class);
        when(profile.getTargetLow()).thenReturn(targetLow);
        when(profile.getTargetHigh()).thenReturn(targetHigh);
        when(profile.getIsf()).thenReturn(insulinSensitivityFactor);
        when(profile.getIc()).thenReturn(insulinToCarbRatio);

        PowerMockito.mockStatic(GlucoseStatus.class);
        when(GlucoseStatus.getGlucoseStatusData()).thenReturn(null);

        PowerMockito.mockStatic(TreatmentsPlugin.class);
        TreatmentsPlugin treatment = mock(TreatmentsPlugin.class);
        IobTotal iobTotalZero = new IobTotal(System.currentTimeMillis());
        when(treatment.getLastCalculationTreatments()).thenReturn(iobTotalZero);
        when(treatment.getLastCalculationTempBasals()).thenReturn(iobTotalZero);
        PowerMockito.mockStatic(MainApp.class);
        when(TreatmentsPlugin.getPlugin()).thenReturn(treatment);

        PowerMockito.mockStatic(ConfigBuilderPlugin.class);
        PumpInterface pump = MDIPlugin.getPlugin();
        pump.getPumpDescription().bolusStep = PUMP_BOLUS_STEP;
        when(ConfigBuilderPlugin.getActivePump()).thenReturn(pump);

        return profile;
    }
}
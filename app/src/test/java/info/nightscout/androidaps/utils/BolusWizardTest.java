package info.nightscout.androidaps.utils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.ConstraintChecker;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.plugins.pump.mdi.MDIPlugin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by kuchjir on 12/12/2017.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, GlucoseStatus.class, ConfigBuilderPlugin.class, TreatmentsPlugin.class, ConstraintChecker.class, ProfileFunctions.class})
public class BolusWizardTest {
    private static final double PUMP_BOLUS_STEP = 0.1;

    @Test
    /** Should calculate the same bolus when different blood glucose but both in target range */
    public void shouldCalculateTheSameBolusWhenBGsInRange() throws Exception {
        Profile profile = setupProfile(4d, 8d, 20d, 12d);

        BolusWizard bw = new BolusWizard(profile, "", null, 20, 0.0, 4.2, 0d, 100d, true, true, true, true, false, false, false);
        Double bolusForBg42 = bw.getCalculatedTotalInsulin();
        bw = new BolusWizard(profile, "", null, 20, 0.0, 5.4, 0d, 100d, true, true, true, true, false, false, false);
        Double bolusForBg54 = bw.getCalculatedTotalInsulin();
        Assert.assertEquals(bolusForBg42, bolusForBg54);
    }

    @Test
    public void shouldCalculateHigherBolusWhenHighBG() throws Exception {
        Profile profile = setupProfile(4d, 8d, 20d, 12d);

        BolusWizard bw = new BolusWizard(profile, "", null, 20, 0.0, 9.8, 0d, 100d, true, true, true, true, false, false, false);
        Double bolusForHighBg = bw.getCalculatedTotalInsulin();
        bw = new BolusWizard(profile, "", null, 20, 0.0, 5.4, 0d, 100d, true, true, true, true, false, false, false);
        Double bolusForBgInRange = bw.getCalculatedTotalInsulin();
        Assert.assertTrue(bolusForHighBg > bolusForBgInRange);
    }

    @Test
    public void shouldCalculateLowerBolusWhenLowBG() throws Exception {
        Profile profile = setupProfile(4d, 8d, 20d, 12d);

        BolusWizard bw = new BolusWizard(profile, "", null, 20, 0.0, 3.6, 0d, 100d, true, true, true, true, false, false, false);
        Double bolusForLowBg = bw.getCalculatedTotalInsulin();
        bw = new BolusWizard(profile, "", null, 20, 0.0, 5.4, 0d, 100d, true, true, true, true, false, false, false);
        Double bolusForBgInRange = bw.getCalculatedTotalInsulin();
        Assert.assertTrue(bolusForLowBg < bolusForBgInRange);
    }

    private Profile setupProfile(Double targetLow, Double targetHigh, Double insulinSensitivityFactor, Double insulinToCarbRatio) {
        Profile profile = mock(Profile.class);
        when(profile.getTargetLowMgdl()).thenReturn(targetLow);
        when(profile.getTargetHighMgdl()).thenReturn(targetHigh);
        when(profile.getIsfMgdl()).thenReturn(insulinSensitivityFactor);
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

        AAPSMocker.mockConfigBuilder();
        PumpInterface pump = MDIPlugin.getPlugin();
        pump.getPumpDescription().bolusStep = PUMP_BOLUS_STEP;
        when(ConfigBuilderPlugin.getPlugin().getActivePump()).thenReturn(pump);

        AAPSMocker.mockConstraintsChecker();
        Mockito.doAnswer(invocation -> {
            Constraint<Double> constraint = invocation.getArgument(0);
            return constraint;
        }).when(AAPSMocker.constraintChecker).applyBolusConstraints(any(Constraint.class));

        AAPSMocker.mockProfileFunctions();
        return profile;
    }
}
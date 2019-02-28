package info.nightscout.androidaps.plugins.aps.loop;

import android.content.Context;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.ConstraintChecker;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.SP;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, SP.class, Context.class, ProfileFunctions.class, TreatmentsPlugin.class, L.class})
public class APSResultTest {
    VirtualPumpPlugin virtualPumpPlugin;
    TreatmentsPlugin treatmentsPlugin;
    Constraint<Boolean> closedLoopEnabled = new Constraint<>(false);

    @Test
    public void isChangeRequestedTest() {
        APSResult apsResult = new APSResult();

        // BASAL RATE IN TEST PROFILE IS 1U/h

        // **** PERCENT pump ****
        virtualPumpPlugin.getPumpDescription().setPumpDescription(PumpType.Cellnovo1); // % based
        apsResult.usePercent(true);

        // closed loop mode return original request
        closedLoopEnabled.set(true);
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(null);
        apsResult.tempBasalRequested(false);
        Assert.assertEquals(false, apsResult.isChangeRequested());
        apsResult.tempBasalRequested(true).percent(200).duration(30);
        Assert.assertEquals(true, apsResult.isChangeRequested());

        // open loop
        closedLoopEnabled.set(false);
        // no change requested
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(null);
        apsResult.tempBasalRequested(false);
        Assert.assertEquals(false, apsResult.isChangeRequested());

        // request 100% when no temp is running
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(null);
        apsResult.tempBasalRequested(true).percent(100).duration(30);
        Assert.assertEquals(false , apsResult.isChangeRequested());

        // request equal temp
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(new TemporaryBasal().percent(70).duration(30));
        apsResult.tempBasalRequested(true).percent(70).duration(30);
        Assert.assertEquals(false , apsResult.isChangeRequested());

        // request zero temp
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(new TemporaryBasal().percent(10).duration(30));
        apsResult.tempBasalRequested(true).percent(0).duration(30);
        Assert.assertEquals(true , apsResult.isChangeRequested());

        // request high temp
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(new TemporaryBasal().percent(190).duration(30));
        apsResult.tempBasalRequested(true).percent(200).duration(30);
        Assert.assertEquals(true , apsResult.isChangeRequested());

        // request slightly different temp
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(new TemporaryBasal().percent(70).duration(30));
        apsResult.tempBasalRequested(true).percent(80).duration(30);
        Assert.assertEquals(false , apsResult.isChangeRequested());

        // request different temp
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(new TemporaryBasal().percent(70).duration(30));
        apsResult.tempBasalRequested(true).percent(120).duration(30);
        Assert.assertEquals(true , apsResult.isChangeRequested());

        // it should work with absolute temps too
        // request different temp
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(new TemporaryBasal().absolute(1).duration(30));
        apsResult.tempBasalRequested(true).percent(100).duration(30);
        Assert.assertEquals(false , apsResult.isChangeRequested());

        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(new TemporaryBasal().absolute(2).duration(30));
        apsResult.tempBasalRequested(true).percent(50).duration(30);
        Assert.assertEquals(true , apsResult.isChangeRequested());

        // **** ABSOLUTE pump ****
        virtualPumpPlugin.getPumpDescription().setPumpDescription(PumpType.Medtronic_515_715); // U/h based
        apsResult.usePercent(false);

        // open loop
        closedLoopEnabled.set(false);
        // request 100% when no temp is running
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(null);
        apsResult.tempBasalRequested(true).rate(1).duration(30);
        Assert.assertEquals(false , apsResult.isChangeRequested());

        // request equal temp
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(new TemporaryBasal().absolute(2).duration(30));
        apsResult.tempBasalRequested(true).rate(2).duration(30);
        Assert.assertEquals(false , apsResult.isChangeRequested());

        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(new TemporaryBasal().percent(200).duration(30));
        apsResult.tempBasalRequested(true).rate(2).duration(30);
        Assert.assertEquals(false , apsResult.isChangeRequested());

        // request zero temp
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(new TemporaryBasal().absolute(0.1d).duration(30));
        apsResult.tempBasalRequested(true).rate(0).duration(30);
        Assert.assertEquals(true , apsResult.isChangeRequested());

        // request high temp
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(new TemporaryBasal().absolute(34.9).duration(30));
        apsResult.tempBasalRequested(true).rate(35).duration(30);
        Assert.assertEquals(true , apsResult.isChangeRequested());

        // request slightly different temp
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(new TemporaryBasal().absolute(1.1d).duration(30));
        apsResult.tempBasalRequested(true).rate(1.2d).duration(30);
        Assert.assertEquals(false , apsResult.isChangeRequested());

        // request different temp
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(new TemporaryBasal().absolute(1.1d).duration(30));
        apsResult.tempBasalRequested(true).rate(1.5d).duration(30);
        Assert.assertEquals(true , apsResult.isChangeRequested());

        // it should work with percent temps too
        // request different temp
        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(new TemporaryBasal().percent(110).duration(30));
        apsResult.tempBasalRequested(true).rate(1.1d).duration(30);
        Assert.assertEquals(false , apsResult.isChangeRequested());

        when(treatmentsPlugin.getTempBasalFromHistory(anyLong())).thenReturn(new TemporaryBasal().percent(200).duration(30));
        apsResult.tempBasalRequested(true).rate(0.5d).duration(30);
        Assert.assertEquals(true , apsResult.isChangeRequested());

    }

    @Test
    public void cloneTest() {
        APSResult apsResult = new APSResult();
        apsResult.rate(10);

        APSResult apsResult2 = apsResult.clone();
        Assert.assertEquals(apsResult.rate, apsResult2.rate, 0);
    }

    @Test
    public void jsonTest() {
        closedLoopEnabled.set(true);
        APSResult apsResult = new APSResult();
        apsResult.rate(20).tempBasalRequested(true);
        Assert.assertEquals(20d, JsonHelper.safeGetDouble(apsResult.json(), "rate"), 0d);

        apsResult.rate(20).tempBasalRequested(false);
        Assert.assertEquals(false, apsResult.json().has("rate"));
    }

    @Before
    public void prepareMock() throws Exception {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockSP();
        AAPSMocker.mockStrings();
        AAPSMocker.mockBus();
        AAPSMocker.mockProfileFunctions();
        AAPSMocker.mockTreatmentService();
        AAPSMocker.mockL();
        treatmentsPlugin = AAPSMocker.mockTreatmentPlugin();
        ConstraintChecker constraintChecker = AAPSMocker.mockConstraintsChecker();

        virtualPumpPlugin = VirtualPumpPlugin.getPlugin();
        when(ConfigBuilderPlugin.getPlugin().getActivePump()).thenReturn(virtualPumpPlugin);

        when(constraintChecker.isClosedLoopAllowed()).thenReturn(closedLoopEnabled);

        Mockito.when(SP.getDouble(anyInt(), anyDouble())).thenReturn(30d);

    }
}

package info.nightscout.androidaps.queue;

import android.content.Context;

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import junit.framework.Assert;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Date;

import info.AAPSMocker;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.ConstraintChecker;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.queue.commands.Command;
import info.nightscout.androidaps.utils.ToastUtils;

import static info.nightscout.androidaps.utils.DateUtil.now;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by mike on 14.01.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, ToastUtils.class, Context.class, TreatmentsPlugin.class})
public class CommandQueueTest extends CommandQueue {

    String validProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"},{\"time\":\"2:00\",\"value\":\"110\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}";
    Profile profile = new Profile(new JSONObject(validProfile), Constants.MGDL);

    public CommandQueueTest() throws JSONException {
    }

    @Test
    public void doTests() throws Exception {
        prepareMock(0d, 0);

        // start with empty queue
        Assert.assertEquals(0, size());

        // add bolus command
        bolus(new DetailedBolusInfo(), null);
        Assert.assertEquals(1, size());

        // add READSTATUS
        readStatus("anyString", null);
        Assert.assertEquals(2, size());

        // adding another bolus should remove the first one (size still == 2)
        bolus(new DetailedBolusInfo(), null);
        Assert.assertEquals(2, size());

        // clear the queue should reset size
        clear();
        Assert.assertEquals(0, size());

        // add tempbasal
        tempBasalAbsolute(0, 30, true, profile, null);
        Assert.assertEquals(1, size());

        // add tempbasal percent. it should replace previous TEMPBASAL
        tempBasalPercent(0, 30, true, profile, null);
        Assert.assertEquals(1, size());

        // add extended bolus
        extendedBolus(1, 30, null);
        Assert.assertEquals(2, size());

        // add cancel temp basal should remove previous 2 temp basal setting
        extendedBolus(1, 30, null);
        Assert.assertEquals(2, size());

        // cancel extended bolus should replace previous extended
        extendedBolus(1, 30, null);
        Assert.assertEquals(2, size());

        // add setProfile
        setProfile(profile, null);
        Assert.assertEquals(3, size());

        // add loadHistory
        loadHistory((byte) 0, null);
        Assert.assertEquals(4, size());

        // add loadEvents
        loadEvents(null);
        Assert.assertEquals(5, size());

        clear();
        tempBasalAbsolute(0, 30, true, profile, null);
        pickup();
        Assert.assertEquals(0, size());
        Assert.assertNotNull(performing);
        Assert.assertEquals(Command.CommandType.TEMPBASAL, performing.commandType);
        resetPerforming();
        Assert.assertNull(performing);
    }

    private void prepareMock(Double insulin, Integer carbs) throws Exception {
        ConstraintChecker constraintChecker = mock(ConstraintChecker.class);

        AAPSMocker.mockMainApp();
        AAPSMocker.mockConfigBuilder();
        PumpInterface pump = VirtualPumpPlugin.getPlugin();
        when(ConfigBuilderPlugin.getPlugin().getActivePump()).thenReturn(pump);

        when(MainApp.getConstraintChecker()).thenReturn(constraintChecker);
        when(MainApp.isEngineeringModeOrRelease()).thenReturn(true);
        Constraint<Double> bolusConstraint = new Constraint<>(0d);
        when(MainApp.getConstraintChecker().applyBolusConstraints(any())).thenReturn(bolusConstraint);
        when(MainApp.getConstraintChecker().applyExtendedBolusConstraints(any())).thenReturn(bolusConstraint);
        Constraint<Integer> carbsConstraint = new Constraint<>(0);
        when(MainApp.getConstraintChecker().applyCarbsConstraints(any())).thenReturn(carbsConstraint);
        Constraint<Double> rateConstraint = new Constraint<>(0d);
        when(MainApp.getConstraintChecker().applyBasalConstraints(any(), any())).thenReturn(rateConstraint);
        Constraint<Integer> percentageConstraint = new Constraint<>(0);
        when(MainApp.getConstraintChecker().applyBasalPercentConstraints(any(), any())).thenReturn(percentageConstraint);

        PowerMockito.mockStatic(ToastUtils.class);
        Context context = mock(Context.class);
        String message = null;
        PowerMockito.doNothing().when(ToastUtils.class, "showToastInUiThread", context, message);

        Bus bus = new Bus(ThreadEnforcer.ANY);

        when(MainApp.bus()).thenReturn(bus);
        when(MainApp.gs(0)).thenReturn("");

        PowerMockito.mockStatic(TreatmentsPlugin.class);
        TreatmentsPlugin treatmentsPlugin = mock(TreatmentsPlugin.class);
        when(TreatmentsPlugin.getPlugin()).thenReturn(treatmentsPlugin);
        when(treatmentsPlugin.getLastBolusTime()).thenReturn(new Date(100, 0,1 ).getTime());
    }

    @Override
    protected synchronized void notifyAboutNewCommand() {
    }

    @Override
    protected void showBolusProgressDialog(Double insulin, Context context) {
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        return false;
    }

    @Test
    public void callingCancelAllBolusesClearsQueue() throws Exception {
        // given
        prepareMock(0d, 0);
        Assert.assertEquals(0, size());

        DetailedBolusInfo smb = new DetailedBolusInfo();
        smb.lastKnownBolusTime = now();
        smb.isSMB = true;
        bolus(smb, null);

        bolus(new DetailedBolusInfo(), null);
        Assert.assertEquals(2, size());

        // when
        cancelAllBoluses();

        // then
        Assert.assertEquals(0, size());
    }

    @Test
    public void smbIsRejectedIfABolusIsQueued() throws Exception {
        // given
        prepareMock(0d, 0);
        Assert.assertEquals(0, size());

        // when
        bolus(new DetailedBolusInfo(), null);

        DetailedBolusInfo smb = new DetailedBolusInfo();
        smb.isSMB = true;
        boolean queued = bolus(smb, null);

        // then
        Assert.assertFalse(queued);
        Assert.assertEquals(size(), 1);
    }

    @Test
    public void smbIsRejectedIfLastKnownBolusIsOutdated() throws Exception {
        // given
        prepareMock(0d, 0);
        Assert.assertEquals(0, size());

        // when
        DetailedBolusInfo bolus = new DetailedBolusInfo();
        bolus.isSMB = true;
        bolus.lastKnownBolusTime = 0;
        boolean queued = bolus(bolus, null);

        // then
        Assert.assertFalse(queued);
        Assert.assertEquals(size(), 0);
    }
}

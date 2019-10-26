package info.nightscout.androidaps.plugins.general.automation.actions;

import com.google.common.base.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration;
import info.nightscout.androidaps.plugins.general.automation.elements.InputTempTarget;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.SP;

import static org.mockito.ArgumentMatchers.any;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, TreatmentsPlugin.class, ProfileFunctions.class})
public class ActionStartTempTargetTest {
    private ActionStartTempTarget actionStartTempTarget;
    private TreatmentsPlugin treatmentsPlugin;
    private TempTarget tempTargetAdded;

    @Test
    public void friendlyNameTest() {
        Assert.assertEquals(R.string.starttemptarget, actionStartTempTarget.friendlyName());
    }

    @Test
    public void shortDescriptionTest() {
        actionStartTempTarget = new ActionStartTempTarget();
        actionStartTempTarget.reason = "Test";
        actionStartTempTarget.value = new InputTempTarget().setValue(100).setUnits(Constants.MGDL);
        actionStartTempTarget.duration = new InputDuration(30, InputDuration.TimeUnit.MINUTES);
        Assert.assertEquals("Start temp target: 100mg/dl@null(Test)", actionStartTempTarget.shortDescription());
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.icon_cp_cgm_target), actionStartTempTarget.icon());
    }

    @Test
    public void doActionTest() {

        actionStartTempTarget.doAction(new Callback() {
            @Override
            public void run() {
            }
        });
        Assert.assertNotEquals(null, tempTargetAdded);
    }

    @Test
    public void hasDialogTest() {
        Assert.assertTrue(actionStartTempTarget.hasDialog());
    }

    @Test
    public void toJSONTest() {
        actionStartTempTarget = new ActionStartTempTarget();
        actionStartTempTarget.reason = "Test";
        actionStartTempTarget.value = new InputTempTarget().setValue(100).setUnits(Constants.MGDL);
        actionStartTempTarget.duration = new InputDuration(30, InputDuration.TimeUnit.MINUTES);
        Assert.assertEquals("{\"data\":{\"reason\":\"Test\",\"durationInMinutes\":30,\"units\":\"mg/dl\",\"value\":100},\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionStartTempTarget\"}", actionStartTempTarget.toJSON());
    }

    @Test
    public void fromJSONTest() {
        actionStartTempTarget = new ActionStartTempTarget();
        actionStartTempTarget.fromJSON("{\"reason\":\"Test\",\"value\":100,\"durationInMinutes\":30,\"units\":\"mg/dl\"}");
        Assert.assertEquals(Constants.MGDL, actionStartTempTarget.value.getUnits());
        Assert.assertEquals(100, actionStartTempTarget.value.getValue(), 0.001d);
        Assert.assertEquals(30, actionStartTempTarget.duration.getMinutes(), 0.001);
        Assert.assertEquals("Test", actionStartTempTarget.reason);
    }

    @Before
    public void prepareTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockSP();
        AAPSMocker.mockStrings();
        AAPSMocker.mockProfileFunctions();
        treatmentsPlugin = AAPSMocker.mockTreatmentPlugin();

        Mockito.doAnswer(invocation -> {
            tempTargetAdded = invocation.getArgument(0);
            return null;
        }).when(treatmentsPlugin).addToHistoryTempTarget(any(TempTarget.class));

        actionStartTempTarget = new ActionStartTempTarget();
    }
}

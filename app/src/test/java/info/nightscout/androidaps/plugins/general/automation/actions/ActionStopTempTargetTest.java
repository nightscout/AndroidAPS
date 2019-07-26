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
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.SP;

import static org.mockito.ArgumentMatchers.any;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, TreatmentsPlugin.class})
public class ActionStopTempTargetTest {
    ActionStopTempTarget actionStopTempTarget = new ActionStopTempTarget();
    TreatmentsPlugin treatmentsPlugin;
    TempTarget tempTargetAdded;

    @Test
    public void friendlyNameTest() {
        Assert.assertEquals(R.string.stoptemptarget, actionStopTempTarget.friendlyName());
    }

    @Test
    public void shortDescriptionTest() {
        Assert.assertEquals("Stop temp target", actionStopTempTarget.shortDescription());
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_stop_24dp), actionStopTempTarget.icon());
    }

    @Test
    public void doActionTest() {

        actionStopTempTarget.doAction(new Callback() {
            @Override
            public void run() {
            }
        });
        Assert.assertNotEquals(null, tempTargetAdded);
    }

    @Test
    public void hasDialogTest() {
        Assert.assertFalse(actionStopTempTarget.hasDialog());
    }

    @Test
    public void toJSONTest() {
        actionStopTempTarget = new ActionStopTempTarget();
        actionStopTempTarget.reason = "Test";
        Assert.assertEquals("{\"data\":{\"reason\":\"Test\"},\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionStopTempTarget\"}", actionStopTempTarget.toJSON());
    }

    @Test
    public void fromJSONTest() {
        actionStopTempTarget = new ActionStopTempTarget();
        actionStopTempTarget.fromJSON("{\"reason\":\"Test\"}");
        Assert.assertEquals("Test", actionStopTempTarget.reason);
    }

    @Before
    public void prepareTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockSP();
        AAPSMocker.mockStrings();
        treatmentsPlugin = AAPSMocker.mockTreatmentPlugin();

        Mockito.doAnswer(invocation -> {
            tempTargetAdded = invocation.getArgument(0);
            return null;
        }).when(treatmentsPlugin).addToHistoryTempTarget(any(TempTarget.class));
    }
}

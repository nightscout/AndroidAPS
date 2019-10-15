package info.nightscout.androidaps.plugins.general.automation.triggers;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ProfileFunctions.class, DateUtil.class, TreatmentsPlugin.class})
public class TriggerBolusAgoTest {

    long now = 1514766900000L;

    @Test
    public void shouldRunTest() {
        when(DateUtil.now()).thenReturn(now); // set current time to now + 10 min
        TriggerBolusAgo t = new TriggerBolusAgo().setValue(110).comparator(Comparator.Compare.IS_EQUAL).lastRun(now - 1);
        Assert.assertFalse(t.shouldRun());
        when(TreatmentsPlugin.getPlugin().getLastBolusTime(false)).thenReturn(now); // Set last bolus time to now
        when(DateUtil.now()).thenReturn(now + (10*60*1000)); // set current time to now + 10 min
        t = new TriggerBolusAgo().setValue(110).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertEquals(110, t.getValue(), 0.01d);
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t.getComparator().getValue());
        Assert.assertFalse(t.shouldRun());
        t = new TriggerBolusAgo().setValue(10).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertEquals(10, t.getValue(), 0.01d);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerBolusAgo().setValue(5).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerBolusAgo().setValue(310).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerBolusAgo().setValue(420).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerBolusAgo().setValue(390).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerBolusAgo().setValue(390).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerBolusAgo().setValue(2).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerBolusAgo().setValue(390).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertTrue(t.shouldRun());

        when(TreatmentsPlugin.getPlugin().getLastBolusTime(false)).thenReturn(0l); // Set last bolus time to 0
        t = new TriggerBolusAgo().comparator(Comparator.Compare.IS_NOT_AVAILABLE);
        Assert.assertTrue(t.shouldRun());

        t = new TriggerBolusAgo().setValue(214).comparator(Comparator.Compare.IS_EQUAL).lastRun(now - 1);
        Assert.assertFalse(t.shouldRun());

    }

    @Test
    public void copyConstructorTest() {
        TriggerBolusAgo t = new TriggerBolusAgo().setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        TriggerBolusAgo t1 = (TriggerBolusAgo) t.duplicate();
        Assert.assertEquals(213, t1.getValue(), 0.01d);
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.getComparator().getValue());
    }

    @Test
    public void executeTest() {
        TriggerBolusAgo t = new TriggerBolusAgo().setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        t.executed(1);
        Assert.assertEquals(1l, t.getLastRun());
    }

    String LBJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"lastRun\":0,\"minutesAgo\":410},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerBolusAgo\"}";

    @Test
    public void toJSONTest() {
        TriggerBolusAgo t = new TriggerBolusAgo().setValue(410).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertEquals(LBJson, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerBolusAgo t = new TriggerBolusAgo().setValue(410).comparator(Comparator.Compare.IS_EQUAL);

        TriggerBolusAgo t2 = (TriggerBolusAgo) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.getComparator().getValue());
        Assert.assertEquals(410, t2.getValue(), 0.01d);
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.icon_bolus), new TriggerBolusAgo().icon());
    }


    @Before
    public void mock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockProfileFunctions();
        PowerMockito.mockStatic(DateUtil.class);
        AAPSMocker.mockTreatmentPlugin();
        when(DateUtil.now()).thenReturn(now);

    }

}

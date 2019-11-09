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
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.utils.DateUtil;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ProfileFunctions.class, DateUtil.class, IobCobCalculatorPlugin.class})
public class TriggerIobTest {

    long now = 1514766900000L;
    IobCobCalculatorPlugin iobCobCalculatorPlugin;

    @Test
    public void shouldRunTest() {
        PowerMockito.when(iobCobCalculatorPlugin.calculateFromTreatmentsAndTempsSynchronized(anyLong(), any(Profile.class))).thenReturn(generateIobRecordData());

        TriggerIob t = new TriggerIob().setValue(1.1d).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerIob().setValue(1d).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerIob().setValue(0.8d).comparator(Comparator.Compare.IS_GREATER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerIob().setValue(0.8d).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerIob().setValue(0.9d).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerIob().setValue(1.2d).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerIob().setValue(1.1d).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerIob().setValue(1d).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerIob().setValue(0.9d).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertFalse(t.shouldRun());

        t = new TriggerIob().setValue(1d).comparator(Comparator.Compare.IS_EQUAL).lastRun(now - 1);
        Assert.assertFalse(t.shouldRun());

    }

    @Test
    public void copyConstructorTest() {
        TriggerIob t = new TriggerIob().setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        TriggerIob t1 = (TriggerIob) t.duplicate();
        Assert.assertEquals(213d, t.getValue(), 0.01d);
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.getComparator().getValue());
    }

    @Test
    public void executeTest() {
        TriggerIob t = new TriggerIob().setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        t.executed(1);
        Assert.assertEquals(1l, t.getLastRun());
    }

    String bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"insulin\":4.1,\"lastRun\":0},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerIob\"}";

    @Test
    public void toJSONTest() {
        TriggerIob t = new TriggerIob().setValue(4.1d).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertEquals(bgJson, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerIob t = new TriggerIob().setValue(4.1d).comparator(Comparator.Compare.IS_EQUAL);

        TriggerIob t2 = (TriggerIob) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.getComparator().getValue());
        Assert.assertEquals(4.1d, t2.getValue(), 0.01d);
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_keyboard_capslock), new TriggerIob().icon());
    }


    @Before
    public void mock() {
        AAPSMocker.mockMainApp();
        iobCobCalculatorPlugin = AAPSMocker.mockIobCobCalculatorPlugin();
        AAPSMocker.mockProfileFunctions();

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(now);

    }

    IobTotal generateIobRecordData() {
        IobTotal iobTotal = new IobTotal(1);
        iobTotal.iob = 1;
        return iobTotal;
    }

}

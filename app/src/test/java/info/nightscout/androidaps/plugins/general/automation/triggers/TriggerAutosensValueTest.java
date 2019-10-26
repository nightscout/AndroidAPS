package info.nightscout.androidaps.plugins.general.automation.triggers;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ProfileFunctions.class, DateUtil.class, IobCobCalculatorPlugin.class, SP.class})
public class TriggerAutosensValueTest {

    long now = 1514766900000L;

    @Test
    public void shouldRunTest() {
        Mockito.when(SP.getDouble(Mockito.eq("openapsama_autosens_max"), anyDouble())).thenReturn(1.2d);
        Mockito.when(SP.getDouble(Mockito.eq("openapsama_autosens_min"), anyDouble())).thenReturn(0.7d);
        when(IobCobCalculatorPlugin.getPlugin().getLastAutosensData("Automation trigger")).thenReturn(generateAutosensData());
        TriggerAutosensValue t = new TriggerAutosensValue().setValue(110).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertEquals(110, t.getValue(), 0.01d);
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t.getComparator().getValue());
        Assert.assertFalse(t.shouldRun());
        t = new TriggerAutosensValue().setValue(100).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertEquals(100, t.getValue(), 0.01d);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerAutosensValue().setValue(50).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerAutosensValue().setValue(310).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerAutosensValue().setValue(420).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerAutosensValue().setValue(390).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerAutosensValue().setValue(390).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerAutosensValue().setValue(20).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerAutosensValue().setValue(390).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertTrue(t.shouldRun());

        when(IobCobCalculatorPlugin.getPlugin().getLastAutosensData("Automation trigger")).thenReturn(new AutosensData());
        t = new TriggerAutosensValue().setValue(80).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertFalse(t.shouldRun());

        // Test autosensData == null and Comparator == IS_NOT_AVAILABLE
        when(IobCobCalculatorPlugin.getPlugin().getLastAutosensData("Automation trigger")).thenReturn(null);
        t = new TriggerAutosensValue().comparator(Comparator.Compare.IS_NOT_AVAILABLE);
        Assert.assertTrue(t.shouldRun());

        t = new TriggerAutosensValue().setValue(214).comparator(Comparator.Compare.IS_EQUAL).lastRun(now - 1);
        Assert.assertFalse(t.shouldRun());

    }

    @Test
    public void copyConstructorTest() {
        TriggerAutosensValue t = new TriggerAutosensValue().setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        TriggerAutosensValue t1 = (TriggerAutosensValue) t.duplicate();
        Assert.assertEquals(213, t1.getValue(), 0.01d);
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.getComparator().getValue());
    }

    @Test
    public void executeTest() {
        TriggerAutosensValue t = new TriggerAutosensValue().setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        t.executed(1);
        Assert.assertEquals(1l, t.getLastRun());
    }

    String ASJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"lastRun\":0,\"value\":410},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerAutosensValue\"}";

    @Test
    public void toJSONTest() {
        TriggerAutosensValue t = new TriggerAutosensValue().setValue(410).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertEquals(ASJson, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerAutosensValue t = new TriggerAutosensValue().setValue(410).comparator(Comparator.Compare.IS_EQUAL);

        TriggerAutosensValue t2 = (TriggerAutosensValue) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.getComparator().getValue());
        Assert.assertEquals(410, t2.getValue(), 0.01d);
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.as), new TriggerAutosensValue().icon());
    }


    @Before
    public void mock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockIobCobCalculatorPlugin();
        AAPSMocker.mockProfileFunctions();
        AAPSMocker.mockSP();
        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(now);

    }

    AutosensData generateAutosensData() {
        AutosensData data = new AutosensData();
        return data;
    }

}

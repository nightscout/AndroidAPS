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
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ProfileFunctions.class, DateUtil.class, IobCobCalculatorPlugin.class, SP.class, L.class})
public class TriggerCOBTest {

    long now = 1514766900000L;
    IobCobCalculatorPlugin iobCobCalculatorPlugin;

    @Test
    public void shouldRunTest() {
        // COB value is 6
        PowerMockito.when(IobCobCalculatorPlugin.getPlugin().getCobInfo(false, "AutomationTriggerCOB")).thenReturn(new CobInfo(6d, 2d));        TriggerCOB t = new TriggerCOB().setValue(1).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerCOB().setValue(6).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerCOB().setValue(5).comparator(Comparator.Compare.IS_GREATER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerCOB().setValue(5).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerCOB().setValue(6).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerCOB().setValue(1).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerCOB().setValue(10).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerCOB().setValue(5).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertFalse(t.shouldRun());

        t = new TriggerCOB().setValue(1).comparator(Comparator.Compare.IS_EQUAL).lastRun(now - 1);
        Assert.assertFalse(t.shouldRun());

    }

    @Test
    public void copyConstructorTest() {
        TriggerCOB t = new TriggerCOB().setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        TriggerCOB t1 = (TriggerCOB) t.duplicate();
        Assert.assertEquals(213d, t.getValue(), 0.01d);
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.getComparator().getValue());
    }

    @Test
    public void executeTest() {
        TriggerCOB t = new TriggerCOB().setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        t.executed(1);
        Assert.assertEquals(1l, t.getLastRun());
    }

    String bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"lastRun\":0,\"carbs\":4},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerCOB\"}";

    @Test
    public void toJSONTest() {
        TriggerCOB t = new TriggerCOB().setValue(4).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertEquals(bgJson, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerCOB t = new TriggerCOB().setValue(4).comparator(Comparator.Compare.IS_EQUAL);

        TriggerCOB t2 = (TriggerCOB) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.getComparator().getValue());
        Assert.assertEquals(4, t2.getValue(), 0.01d);
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.icon_cp_bolus_carbs), new TriggerCOB().icon());
    }


    @Before
    public void mock() {
        AAPSMocker.mockMainApp();
        iobCobCalculatorPlugin = AAPSMocker.mockIobCobCalculatorPlugin();
        AAPSMocker.mockProfileFunctions();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(now);
        when(SP.getInt(any(), any())).thenReturn(48);
    }

    public CobInfo generateCobInfo(){
        return new CobInfo(6d, 0d);
    }

}

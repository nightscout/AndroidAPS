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
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ProfileFunctions.class, DateUtil.class, TreatmentsPlugin.class, ConfigBuilderPlugin.class, System.class})
public class TriggerPumpLastConnectionTest {

    long now = 1514766900000L;

    @Test
    public void shouldRunTest() {
//        System.currentTimeMillis() is always 0
//        and so is every last connection time
        VirtualPumpPlugin virtualPumpPlugin = VirtualPumpPlugin.getPlugin();
        when(ConfigBuilderPlugin.getPlugin().getActivePump()).thenReturn(virtualPumpPlugin);
        Assert.assertEquals(0L, virtualPumpPlugin.lastDataTime());
        TriggerPumpLastConnection t = new TriggerPumpLastConnection().setValue(110).comparator(Comparator.Compare.IS_EQUAL).lastRun(now - 1);
        Assert.assertFalse(t.shouldRun());
        when(DateUtil.now()).thenReturn(now + (10*60*1000)); // set current time to now + 10 min
        t = new TriggerPumpLastConnection().setValue(110).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertEquals(110, t.getValue(), 0.01d);
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t.getComparator().getValue());
        Assert.assertFalse(t.shouldRun());
        t = new TriggerPumpLastConnection().setValue(10).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertEquals(10, t.getValue(), 0.01d);
        Assert.assertFalse(t.shouldRun()); // 0 == 10 -> FALSE
        t = new TriggerPumpLastConnection().setValue(5).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertTrue(t.shouldRun()); // 5 => 0 -> TRUE
        t = new TriggerPumpLastConnection().setValue(310).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertFalse(t.shouldRun()); // 310 <= 0 -> FALSE
        t = new TriggerPumpLastConnection().setValue(420).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertFalse(t.shouldRun()); // 420 == 0 -> FALSE
    }

    @Test
    public void copyConstructorTest() {
        TriggerPumpLastConnection t = new TriggerPumpLastConnection().setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        TriggerPumpLastConnection t1 = (TriggerPumpLastConnection) t.duplicate();
        Assert.assertEquals(213, t1.getValue(), 0.01d);
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.getComparator().getValue());
    }

    @Test
    public void executeTest() {
        TriggerPumpLastConnection t = new TriggerPumpLastConnection().setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        t.executed(1);
        Assert.assertEquals(1l, t.getLastRun());
    }

    String LBJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"lastRun\":0,\"minutesAgo\":410},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerPumpLastConnection\"}";

    @Test
    public void toJSONTest() {
        TriggerPumpLastConnection t = new TriggerPumpLastConnection().setValue(410).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertEquals(LBJson, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerPumpLastConnection t = new TriggerPumpLastConnection().setValue(410).comparator(Comparator.Compare.IS_EQUAL);

        TriggerPumpLastConnection t2 = (TriggerPumpLastConnection) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.getComparator().getValue());
        Assert.assertEquals(410, t2.getValue(), 0.01d);
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.remove), new TriggerPumpLastConnection().icon());
    }

    @Test
    public void friendlyNameTest() {
        Assert.assertEquals(R.string.automation_trigger_pump_last_connection_label, new TriggerPumpLastConnection().friendlyName());
    }


    @Before
    public void mock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockConfigBuilder();
        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(now);

    }

}

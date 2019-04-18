package info.nightscout.androidaps.plugins.general.automation.triggers;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.SP;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, L.class, SP.class})
public class TriggerConnectorTest {

    @Test
    public void testTriggerList() {
        TriggerConnector t = new TriggerConnector();
        TriggerConnector t2 = new TriggerConnector();
        TriggerConnector t3 = new TriggerConnector();

        Assert.assertTrue(t.size() == 0);

        t.add(t2);
        Assert.assertTrue(t.size() == 1);
        Assert.assertEquals(t2, t.get(0));

        t.add(t3);
        Assert.assertTrue(t.size() == 2);
        Assert.assertEquals(t2, t.get(0));
        Assert.assertEquals(t3, t.get(1));

        Assert.assertTrue(t.remove(t2));
        Assert.assertTrue(t.size() == 1);
        Assert.assertEquals(t3, t.get(0));

        Assert.assertTrue(t.shouldRun());
    }

    @Test
    public void testListTriggerOR() {
        TriggerConnector t = new TriggerConnector(TriggerConnector.Type.OR);
        t.add(new DummyTrigger(false));
        t.add(new DummyTrigger(false));
        Assert.assertFalse(t.shouldRun());

        t.add(new DummyTrigger(true));
        t.add(new DummyTrigger(false));
        Assert.assertTrue(t.shouldRun());
    }

    @Test
    public void testListTriggerXOR() {
        TriggerConnector t = new TriggerConnector(TriggerConnector.Type.XOR);
        t.add(new DummyTrigger(false));
        t.add(new DummyTrigger(false));
        Assert.assertFalse(t.shouldRun());

        t.add(new DummyTrigger(true));
        t.add(new DummyTrigger(false));
        Assert.assertTrue(t.shouldRun());

        t.add(new DummyTrigger(true));
        t.add(new DummyTrigger(false));
        Assert.assertFalse(t.shouldRun());
    }

    @Test
    public void testListTriggerAND() {
        TriggerConnector t = new TriggerConnector(TriggerConnector.Type.AND);
        t.add(new DummyTrigger(true));
        t.add(new DummyTrigger(true));
        Assert.assertTrue(t.shouldRun());

        t.add(new DummyTrigger(true));
        t.add(new DummyTrigger(false));
        Assert.assertFalse(t.shouldRun());
    }

    static public final String empty = "{\"data\":{\"connectorType\":\"AND\",\"triggerList\":[]},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector\"}";
    static public final String oneItem = "{\"data\":{\"connectorType\":\"AND\",\"triggerList\":[\"{\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[]},\\\"type\\\":\\\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector\\\"}\"]},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector\"}";

    @Test
    public void toJSONTest() {
        TriggerConnector t = new TriggerConnector();
        Assert.assertEquals(empty, t.toJSON());
        t.add(new TriggerConnector());
        Assert.assertEquals(oneItem, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerConnector t = new TriggerConnector();
        t.add(new TriggerConnector());

        TriggerConnector t2 = (TriggerConnector) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(1, t2.size());
        Assert.assertTrue(t2.get(0) instanceof TriggerConnector);
    }

    @Before
    public void prepareMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
    }
}

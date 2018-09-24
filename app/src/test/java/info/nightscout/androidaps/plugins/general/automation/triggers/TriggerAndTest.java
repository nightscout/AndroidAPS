package info.nightscout.androidaps.plugins.general.automation.triggers;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({})
public class TriggerAndTest {

    @Test
    public void doTests() {
        TriggerAnd t = new TriggerAnd();
        TriggerAnd t2 = new TriggerAnd();
        TriggerAnd t3 = new TriggerAnd();

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

    String empty = "{\"data\":\"[]\",\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerAnd\"}";
    String oneItem = "{\"data\":\"[\\\"{\\\\\\\"data\\\\\\\":\\\\\\\"[]\\\\\\\",\\\\\\\"type\\\\\\\":\\\\\\\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerAnd\\\\\\\"}\\\"]\",\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerAnd\"}";

    @Test
    public void toJSONTest() {
        TriggerAnd t = new TriggerAnd();
        Assert.assertEquals(empty, t.toJSON());
        t.add(new TriggerAnd());
        Assert.assertEquals(oneItem, t.toJSON());
    }
    @Test
    public void fromJSONTest() throws JSONException {
        TriggerAnd t = new TriggerAnd();
        t.add(new TriggerAnd());

        TriggerAnd t2 = (TriggerAnd) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(1, t2.size());
        Assert.assertTrue(t2.get(0) instanceof TriggerAnd);
    }
}

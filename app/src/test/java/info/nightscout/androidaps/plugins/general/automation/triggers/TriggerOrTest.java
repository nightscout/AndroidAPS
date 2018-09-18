package info.nightscout.androidaps.plugins.general.automation.triggers;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger;
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerOr;

@RunWith(PowerMockRunner.class)
@PrepareForTest({})
public class TriggerOrTest {

    @Test
    public void doTests() {
        TriggerOr t = new TriggerOr();
        TriggerOr t2 = new TriggerOr();
        TriggerOr t3 = new TriggerOr();

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

        Assert.assertFalse(t.shouldRun());
    }

    String empty = "{\"data\":\"[]\",\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerOr\"}";
    String oneItem = "{\"data\":\"[\\\"{\\\\\\\"data\\\\\\\":\\\\\\\"[]\\\\\\\",\\\\\\\"type\\\\\\\":\\\\\\\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerOr\\\\\\\"}\\\"]\",\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerOr\"}";

    @Test
    public void toJSONTest() {
        TriggerOr t = new TriggerOr();
        Assert.assertEquals(empty, t.toJSON());
        t.add(new TriggerOr());
        Assert.assertEquals(oneItem, t.toJSON());
    }
    @Test
    public void fromJSONTest() throws JSONException {
        TriggerOr t = new TriggerOr();
        t.add(new TriggerOr());

        TriggerOr t2 = (TriggerOr) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(1, t2.size());
        Assert.assertTrue(t2.get(0) instanceof TriggerOr);
    }
}

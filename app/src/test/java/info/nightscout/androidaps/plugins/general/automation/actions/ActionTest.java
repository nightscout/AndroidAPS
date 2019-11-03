package info.nightscout.androidaps.plugins.general.automation.actions;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.queue.Callback;

@RunWith(PowerMockRunner.class)
@PrepareForTest({})
public class ActionTest extends Action {

    @Override
    public int friendlyName() {
        return 0;
    }

    @Override
    public String shortDescription() {
        return null;
    }

    @Override
    public void doAction(Callback callback) {
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.absent();
    }

    @Test
    public void hasDialogTest() {
        Assert.assertEquals(false, hasDialog());
        generateDialog(null); // coverage only
    }

    @Test
    public void toJSONTest() {
        Assert.assertEquals("{\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionTest\"}", toJSON());
    }

    @Test
    public void fromJSONTest() {
        Assert.assertEquals(this, fromJSON("any"));
    }

    @Test
    public void instantiateTest() throws JSONException {
        Action action = Action.instantiate(new JSONObject("{\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionTest\"}"));
        Assert.assertNotEquals(null, action);
    }

}

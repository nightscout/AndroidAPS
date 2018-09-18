package info.nightscout.androidaps.plugins.general.automation.actions;

import com.squareup.otto.Bus;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.GregorianCalendar;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.T;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, Bus.class, DateUtil.class, GregorianCalendar.class})
public class TriggerTimeTest {

    long now = 1514766900000L;

    @Test
    public void shouldRunTest() {

        // scheduled 1 min before
        TriggerTime t = new TriggerTime().runAt(now - T.mins(1).msecs());
        Assert.assertTrue(t.shouldRun());
        // scheduled 1 min in the future
        t = new TriggerTime().runAt(now + T.mins(1).msecs());
        Assert.assertFalse(t.shouldRun());

        // limit by validTo
        t = new TriggerTime().recurring(true).hour(1).minute(34).validTo(1);
        Assert.assertFalse(t.shouldRun());

        // scheduled 1 min before
        t = new TriggerTime().recurring(true).hour(1).minute(34);
        Assert.assertTrue(t.shouldRun());

        // already run
        t = new TriggerTime().recurring(true).hour(1).minute(34).lastRun(now - 1);
        Assert.assertFalse(t.shouldRun());

    }

    String timeJson = "{\"data\":\"{\\\"saturday\\\":true,\\\"runAt\\\":1514766840000,\\\"lastRun\\\":0,\\\"recurring\\\":false,\\\"thursday\\\":true,\\\"minute\\\":0,\\\"sunday\\\":true,\\\"tuesday\\\":true,\\\"hour\\\":0,\\\"wednesday\\\":true,\\\"friday\\\":true,\\\"monday\\\":true,\\\"validTo\\\":0}\",\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.TriggerTime\"}";

    @Test
    public void toJSONTest() {
        TriggerTime t = new TriggerTime().runAt(now - T.mins(1).msecs());
        Assert.assertEquals(timeJson, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerTime t = new TriggerTime().runAt(now - T.mins(1).msecs());

        TriggerTime t2 = (TriggerTime) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(now - T.mins(1).msecs(), t2.runAt);
        Assert.assertEquals(false, t2.recurring);
    }

    @Before
    public void mock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockBus();

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(now);

        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(now);
        when(DateUtil.gregorianCalendar()).thenReturn(calendar);
    }
}

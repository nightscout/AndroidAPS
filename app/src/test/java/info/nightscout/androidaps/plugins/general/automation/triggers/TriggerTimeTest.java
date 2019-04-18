package info.nightscout.androidaps.plugins.general.automation.triggers;

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
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.T;

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

        // already run
        t = new TriggerTime().runAt(now - T.mins(1).msecs()).lastRun(now - 1);
        Assert.assertFalse(t.shouldRun());

    }

    String timeJson = "{\"data\":{\"runAt\":1514766840000,\"lastRun\":0},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTime\"}";

    @Test
    public void toJSONTest() {
        TriggerTime t = new TriggerTime().runAt(now - T.mins(1).msecs());
        Assert.assertEquals(timeJson, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerTime t = new TriggerTime().runAt(now - T.mins(1).msecs());

        TriggerTime t2 = (TriggerTime) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(now - T.mins(1).msecs(), t2.getRunAt());
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

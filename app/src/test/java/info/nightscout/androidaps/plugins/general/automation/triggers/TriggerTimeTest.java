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

import java.util.GregorianCalendar;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.T;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, DateUtil.class, GregorianCalendar.class})
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

    @Test
    public void copyConstructorTest() {
        TriggerTime t = new TriggerTime();
        t.runAt(now);

        TriggerTime t1 = (TriggerTime) t.duplicate();
        Assert.assertEquals(now, t1.getRunAt(), 0.01d);
    }

    @Test
    public void friendlyNameTest() {
        Assert.assertEquals(R.string.time, new TriggerTime().friendlyName());
    }

    @Test
    public void friendlyDescriptionTest() {
        Assert.assertEquals(null, new TriggerTime().friendlyDescription()); //not mocked    }
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_access_alarm_24dp), new TriggerTime().icon());

    }

    @Before
    public void mock() {
        AAPSMocker.mockMainApp();

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(now);

        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(now);
        when(DateUtil.gregorianCalendar()).thenReturn(calendar);
    }
}

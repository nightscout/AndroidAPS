package info.nightscout.androidaps.plugins.general.automation.triggers;

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
@PrepareForTest({MainApp.class, DateUtil.class, GregorianCalendar.class})
public class TriggerRecurringTimeTest {

    long now = 1514766900000L;

    @Test
    public void shouldRunTest() {

        // limit by validTo
        TriggerRecurringTime t = new TriggerRecurringTime().hour(1).minute(34).validTo(1);
        t.setAll(true);
        Assert.assertFalse(t.shouldRun());

        // scheduled 1 min before
//        t = new TriggerRecurringTime().hour(1).minute(34);
//        t.setAll(true);
//        Assert.assertTrue(t.shouldRun());

        // already run
        t = new TriggerRecurringTime().hour(1).minute(34).lastRun(now - 1);
        t.setAll(true);
        Assert.assertFalse(t.shouldRun());

    }

    String timeJson = "{\"data\":{\"WEDNESDAY\":false,\"MONDAY\":false,\"THURSDAY\":false,\"lastRun\":1514766840000,\"SUNDAY\":false,\"hour\":0,\"TUESDAY\":false,\"FRIDAY\":false,\"SATURDAY\":false,\"minute\":0,\"validTo\":0},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerRecurringTime\"}";

    @Test
    public void toJSONTest() throws JSONException {
        TriggerRecurringTime t = new TriggerRecurringTime().lastRun(now - T.mins(1).msecs());
        Assert.assertEquals(timeJson, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerRecurringTime t = new TriggerRecurringTime().lastRun(now - T.mins(1).msecs());

        TriggerRecurringTime t2 = (TriggerRecurringTime) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(now - T.mins(1).msecs(), t2.lastRun);    }

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

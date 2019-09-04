package info.nightscout.androidaps.plugins.general.automation.triggers;

import com.google.common.base.Optional;
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
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.T;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, Bus.class, DateUtil.class, GregorianCalendar.class})
public class TriggerTimeOfDayTest {

    int now = 754;
    String timeJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"lastRun\":0,\"minSinceMidnight\":753},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTimeOfDay\"}";

    @Test
    public void shouldRunTest() {

        // scheduled 1 min before
        TriggerTimeOfDay t = new TriggerTimeOfDay().minSinceMidnight(753);

        // scheduled 1 min in the future
        t = new TriggerTimeOfDay().minSinceMidnight(now + 1);
        Assert.assertFalse(t.shouldRun());

        // already run
        t = new TriggerTimeOfDay().minSinceMidnight(now - 1).lastRun(now - 1);
        Assert.assertFalse(t.shouldRun());

    }

    @Test
    public void toJSONTest() {
        TriggerTimeOfDay t = new TriggerTimeOfDay().minSinceMidnight(now - 1);
        Assert.assertEquals(timeJson, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerTimeOfDay t = new TriggerTimeOfDay().minSinceMidnight(120);

        TriggerTimeOfDay t2 = (TriggerTimeOfDay) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(now - 1, t2.minSinceMidnight(753).getMinSinceMidnight());
    }

    @Test
    public void copyConstructorTest() {
        TriggerTimeOfDay t = new TriggerTimeOfDay();
        t.minSinceMidnight(now);

        TriggerTimeOfDay t1 = (TriggerTimeOfDay) t.duplicate();
//        Assert.assertEquals(now, t1.getRunAt(), 0.01d);
    }

    @Test
    public void friendlyNameTest() {
        Assert.assertEquals(R.string.time_of_day, new TriggerTimeOfDay().friendlyName());
    }

    @Test
    public void friendlyDescriptionTest() {
        Assert.assertEquals(null, new TriggerTimeOfDay().friendlyDescription()); //not mocked    }
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_access_alarm_24dp), new TriggerTimeOfDay().icon());

    }

    @Before
    public void mock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockBus();

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn((long) now*60*60*10000);

        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(now);
        when(DateUtil.gregorianCalendar()).thenReturn(calendar);
    }
}

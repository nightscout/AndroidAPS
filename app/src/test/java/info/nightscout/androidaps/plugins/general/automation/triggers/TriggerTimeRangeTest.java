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

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, DateUtil.class, GregorianCalendar.class})
public class TriggerTimeRangeTest {

    int now = 754;
    int timeZoneOffset = (int) (DateUtil.getTimeZoneOffsetMs() / 60000);

    String timeJson = "{\"data\":{\"lastRun\":0,\"start\":753,\"end\":784},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTimeRange\"}";

    @Test
    public void shouldRunTest() {

        TriggerTimeRange t;
        now = now + timeZoneOffset;
        // range starts 1 min in the future
        t = new TriggerTimeRange().period(now + 1 - timeZoneOffset, now + 30 - timeZoneOffset);
        Assert.assertEquals(false, t.shouldRun());

        // range starts 30 min back
        t = new TriggerTimeRange().period(now - 30 - timeZoneOffset, now + 30 - timeZoneOffset);
        Assert.assertEquals(true, t.shouldRun());

        // Period is all day long
        t = new TriggerTimeRange().period(1, 1440);
        Assert.assertEquals(true, t.shouldRun());

        // already run
        t = new TriggerTimeRange().lastRun((long) (now  - 1)*60000);
        Assert.assertFalse(t.shouldRun());

    }

    @Test
    public void toJSONTest() {
        TriggerTimeRange t = new TriggerTimeRange().period(now - 1 - timeZoneOffset , now + 30 - timeZoneOffset);
        Assert.assertEquals(timeJson, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerTimeRange t = new TriggerTimeRange().period(120 , 180);

        TriggerTimeRange t2 = (TriggerTimeRange) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(timeZoneOffset + now - 1, t2.period(753 , 360).getStart());
        Assert.assertEquals(timeZoneOffset + 360, t2.period(753 , 360).getEnd());
    }

    @Test
    public void copyConstructorTest() {
        TriggerTimeRange t = new TriggerTimeRange();
        t.period(now, now + 30);

        TriggerTimeRange t1 = (TriggerTimeRange) t.duplicate();
//        Assert.assertEquals(now, t1.getRunAt(), 0.01d);
    }

    @Test
    public void friendlyNameTest() {
        Assert.assertEquals(R.string.time_range, new TriggerTimeRange().friendlyName());
    }

    @Test
    public void friendlyDescriptionTest() {
        Assert.assertEquals(null, new TriggerTimeRange().friendlyDescription()); //not mocked    }
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_access_alarm_24dp), new TriggerTimeRange().icon());

    }

    @Before
    public void mock() {
        AAPSMocker.mockMainApp();

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn((long) now * 60000);

        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis((long) now * 60000);
        when(DateUtil.gregorianCalendar()).thenReturn(calendar);
    }
}

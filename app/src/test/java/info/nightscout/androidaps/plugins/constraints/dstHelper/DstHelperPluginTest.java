package info.nightscout.androidaps.plugins.constraints.dstHelper;

import android.content.Context;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Calendar;
import java.util.TimeZone;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, SP.class, Context.class})
public class DstHelperPluginTest {
    DstHelperPlugin plugin = new DstHelperPlugin();

    @Test
    public void runTest() throws Exception {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        // test different time zones
        //Starting with Europe/Sofia
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Sofia"));
        c.setTimeInMillis(DateUtil.fromISODateString("2018-10-28T02:00:00Z").getTime());
        int minutesLeftToChange = plugin.dstTest(c);
        Assert.assertEquals(60, minutesLeftToChange);
        c.setTimeInMillis(DateUtil.fromISODateString("2018-03-25T02:00:00Z").getTime());
        minutesLeftToChange = plugin.dstTest(c);
        Assert.assertEquals(60, minutesLeftToChange);
        // try something with half hour somewhere in Australia
        c = Calendar.getInstance(TimeZone.getTimeZone("Australia/Lord_Howe"));
        c.setTimeInMillis(DateUtil.fromISODateString("2018-04-01T00:00:00Z").getTime());
        minutesLeftToChange = plugin.dstTest(c);
        // try something with half hour somewhere in Australia
        c = Calendar.getInstance(TimeZone.getTimeZone("Australia/Lord_Howe"));
        c.setTimeInMillis(DateUtil.fromISODateString("2018-04-01T00:00:00Z").getTime());
        minutesLeftToChange = plugin.dstTest(c);
        Assert.assertEquals(90, minutesLeftToChange);
        c = Calendar.getInstance(TimeZone.getTimeZone("Australia/Lord_Howe"));
        // and back
        c.setTimeInMillis(DateUtil.fromISODateString("2018-10-07T00:00:00Z").getTime());
        minutesLeftToChange = plugin.dstTest(c);
        Assert.assertEquals(120, minutesLeftToChange);

        c.setTimeInMillis(DateUtil.fromISODateString("2018-10-08T00:00:00Z").getTime());
        minutesLeftToChange = plugin.dstTest(c);
        Assert.assertEquals(0, minutesLeftToChange);

        // DST event was 30 mins
        c.setTimeInMillis(DateUtil.fromISODateString("2018-04-01T02:00:00Z").getTime());
        minutesLeftToChange = plugin.dstTest(c);
//        Assert.assertEquals(630, plugin.zoneOffsetInMinutes(c));
        Assert.assertEquals(0, minutesLeftToChange);
    }

}
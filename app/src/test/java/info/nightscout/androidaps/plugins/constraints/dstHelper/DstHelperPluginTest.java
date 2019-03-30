package info.nightscout.androidaps.plugins.constraints.dstHelper;

import android.content.Context;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
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

        TimeZone tz = TimeZone.getTimeZone("Europe/Rome");
        TimeZone.setDefault(tz);
        Calendar cal = Calendar.getInstance(tz, Locale.ITALIAN);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ITALIAN);
        Date dateBeforeDST = df.parse("2018-03-25 01:55");
        cal.setTime(dateBeforeDST);
        Assert.assertEquals(false, plugin.wasDST(cal));
        Assert.assertEquals(true, plugin.willBeDST(cal));


        TimeZone.setDefault(tz);
        cal = Calendar.getInstance(tz, Locale.ITALIAN);
        dateBeforeDST = df.parse("2018-03-25 03:05");
        cal.setTime(dateBeforeDST);
        Assert.assertEquals(true, plugin.wasDST(cal));
        Assert.assertEquals(false, plugin.willBeDST(cal));


        TimeZone.setDefault(tz);
        cal = Calendar.getInstance(tz, Locale.ITALIAN);
        dateBeforeDST = df.parse("2018-03-25 02:05"); //Cannot happen!!!
        cal.setTime(dateBeforeDST);
        Assert.assertEquals(true, plugin.wasDST(cal));
        Assert.assertEquals(false, plugin.willBeDST(cal));

        TimeZone.setDefault(tz);
        cal = Calendar.getInstance(tz, Locale.ITALIAN);
        dateBeforeDST = df.parse("2018-03-25 05:55"); //Cannot happen!!!
        cal.setTime(dateBeforeDST);
        Assert.assertEquals(true, plugin.wasDST(cal));
        Assert.assertEquals(false, plugin.willBeDST(cal));

        TimeZone.setDefault(tz);
        cal = Calendar.getInstance(tz, Locale.ITALIAN);
        dateBeforeDST = df.parse("2018-03-25 06:05"); //Cannot happen!!!
        cal.setTime(dateBeforeDST);
        Assert.assertEquals(false, plugin.wasDST(cal));
        Assert.assertEquals(false, plugin.willBeDST(cal));

    }

}
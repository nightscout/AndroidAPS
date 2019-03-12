package info.nightscout.androidaps.plugins.iob.iobCobCalculatorPlugin;

import android.content.Context;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, SP.class, Context.class, L.class})
public class IobCobCalculatorPluginTest {

    IobCobCalculatorPlugin iobCobCalculatorPlugin = IobCobCalculatorPlugin.getPlugin();

    @Test
    public void isAbout5minDataTest() {
        List<BgReading> bgReadingList = new ArrayList<>();

        // Super data should not be touched
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(15).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(10).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(5).msecs()).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);

        Assert.assertEquals(true, iobCobCalculatorPlugin.isAbout5minData());

        // too much shifted data should return false
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(15).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(9).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(5).msecs()).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);

        Assert.assertEquals(false, iobCobCalculatorPlugin.isAbout5minData());

        // too much shifted and missing data should return false
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(9).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(5).msecs()).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);

        Assert.assertEquals(false, iobCobCalculatorPlugin.isAbout5minData());

        // too much shifted and missing data should return false
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(83).plus(T.secs(40)).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(78).plus(T.secs(40)).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(73).plus(T.secs(40)).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(68).plus(T.secs(40)).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(63).plus(T.secs(40)).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(58).plus(T.secs(40)).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(53).plus(T.secs(40)).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(48).plus(T.secs(40)).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(43).plus(T.secs(40)).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(38).plus(T.secs(33)).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(33).plus(T.secs(1)).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(28).plus(T.secs(0)).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(23).plus(T.secs(0)).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(16).plus(T.secs(36)).msecs()).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);

        Assert.assertEquals(false, iobCobCalculatorPlugin.isAbout5minData());

        // slighly shifted data should return true
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(15).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(10).msecs() - T.secs(10).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(5).msecs()).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);

        Assert.assertEquals(true, iobCobCalculatorPlugin.isAbout5minData());

        // slighly shifted and missing data should return true
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(10).msecs() - T.secs(10).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(5).msecs()).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);

        Assert.assertEquals(true, iobCobCalculatorPlugin.isAbout5minData());

    }

    @Test
    public void createBucketedData5minTest() throws Exception {
        List<BgReading> bgReadingList = new ArrayList<>();

        // Super data should not be touched
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(15).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(10).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(5).msecs()).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);
        Assert.assertEquals(true, iobCobCalculatorPlugin.isAbout5minData());

        iobCobCalculatorPlugin.createBucketedData();

        Assert.assertEquals(bgReadingList.get(0).date, iobCobCalculatorPlugin.getBucketedData().get(0).date);
        Assert.assertEquals(bgReadingList.get(3).date, iobCobCalculatorPlugin.getBucketedData().get(3).date);
        Assert.assertEquals(bgReadingList.size(), iobCobCalculatorPlugin.getBucketedData().size());

        // Missing value should be replaced
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(10).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(5).msecs()).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);
        Assert.assertEquals(true, iobCobCalculatorPlugin.isAbout5minData());

        iobCobCalculatorPlugin.createBucketedData();

        Assert.assertEquals(bgReadingList.get(0).date, iobCobCalculatorPlugin.getBucketedData().get(0).date);
        Assert.assertEquals(bgReadingList.get(2).date, iobCobCalculatorPlugin.getBucketedData().get(3).date);
        Assert.assertEquals(bgReadingList.size() + 1, iobCobCalculatorPlugin.getBucketedData().size());

        // drift should be cleared
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(15).msecs() + T.secs(10).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(10).msecs() + T.secs(10).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(5).msecs() - T.secs(10).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(0).msecs()).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);
        Assert.assertEquals(true, iobCobCalculatorPlugin.isAbout5minData());

        iobCobCalculatorPlugin.createBucketedData();

        Assert.assertEquals(T.mins(20).msecs(), iobCobCalculatorPlugin.getBucketedData().get(0).date);
        Assert.assertEquals(T.mins(15).msecs(), iobCobCalculatorPlugin.getBucketedData().get(1).date);
        Assert.assertEquals(T.mins(10).msecs(), iobCobCalculatorPlugin.getBucketedData().get(2).date);
        Assert.assertEquals(T.mins(5).msecs(), iobCobCalculatorPlugin.getBucketedData().get(3).date);
        Assert.assertEquals(bgReadingList.size(), iobCobCalculatorPlugin.getBucketedData().size());

        // bucketed data should return null if not enough bg data
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(30).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(5).msecs()).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);
        Assert.assertEquals(true, iobCobCalculatorPlugin.isAbout5minData());

        iobCobCalculatorPlugin.createBucketedData();

        Assert.assertEquals(null, iobCobCalculatorPlugin.getBucketedData());

        // data should be reconstructed
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(50).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(45).msecs()).value(90));
        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(40));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);
        Assert.assertEquals(true, iobCobCalculatorPlugin.isAbout5minData());

        iobCobCalculatorPlugin.createBucketedData();

        Assert.assertEquals(T.mins(50).msecs(), iobCobCalculatorPlugin.getBucketedData().get(0).date);
        Assert.assertEquals(T.mins(20).msecs(), iobCobCalculatorPlugin.getBucketedData().get(6).date);
        Assert.assertEquals(7, iobCobCalculatorPlugin.getBucketedData().size());

        Assert.assertEquals(100, iobCobCalculatorPlugin.getBucketedData().get(0).value, 1);
        Assert.assertEquals(90, iobCobCalculatorPlugin.getBucketedData().get(1).value, 1);
        Assert.assertEquals(50, iobCobCalculatorPlugin.getBucketedData().get(5).value, 1);
        Assert.assertEquals(40, iobCobCalculatorPlugin.getBucketedData().get(6).value, 1);

        // non 5min data should be reconstructed
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(50).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(48).msecs()).value(96));
        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(40));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);
        Assert.assertEquals(false, iobCobCalculatorPlugin.isAbout5minData());

        iobCobCalculatorPlugin.createBucketedData();

        Assert.assertEquals(T.mins(50).msecs(), iobCobCalculatorPlugin.getBucketedData().get(0).date);
        Assert.assertEquals(T.mins(20).msecs(), iobCobCalculatorPlugin.getBucketedData().get(6).date);
        Assert.assertEquals(7, iobCobCalculatorPlugin.getBucketedData().size());

        Assert.assertEquals(100, iobCobCalculatorPlugin.getBucketedData().get(0).value, 1);
        Assert.assertEquals(90, iobCobCalculatorPlugin.getBucketedData().get(1).value, 1);
        Assert.assertEquals(50, iobCobCalculatorPlugin.getBucketedData().get(5).value, 1);
        Assert.assertEquals(40, iobCobCalculatorPlugin.getBucketedData().get(6).value, 1);

        //bucketed data should be null if no bg data available
        iobCobCalculatorPlugin.setBgReadings(null);
        iobCobCalculatorPlugin.createBucketedData();
        Assert.assertEquals(null, iobCobCalculatorPlugin.getBucketedData());

        // real data gap test
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T13:34:55Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T13:14:55Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T13:09:55Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T13:04:55Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T12:59:55Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T12:54:55Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T12:49:55Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T12:44:55Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T12:39:55Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T12:34:55Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T12:29:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T12:24:55Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T12:19:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T12:14:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T12:09:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T12:04:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T11:59:55Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T11:54:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T11:49:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T11:44:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T11:39:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T11:34:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T11:29:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T11:24:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T11:19:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T11:14:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T11:09:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T11:04:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T10:59:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T10:54:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T10:49:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T10:44:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T10:39:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T10:34:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T10:29:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T10:24:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T10:19:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T10:14:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T10:09:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T10:04:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T09:59:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T09:54:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T09:49:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T09:44:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T09:39:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T09:35:05Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T09:30:17Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T09:24:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T09:19:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T09:14:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T09:09:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T09:04:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T08:59:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T08:54:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T08:49:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T08:44:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T08:40:02Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T08:34:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T08:29:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T08:24:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T08:19:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T08:14:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T08:09:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T08:04:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T07:59:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T07:54:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T07:49:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T07:44:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T07:39:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T07:34:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T07:30:03Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T07:25:17Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T07:19:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T07:14:58Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T07:09:58Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T07:04:58Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T06:59:58Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T06:54:58Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T06:49:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T06:44:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T06:39:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T06:34:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T06:29:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T06:24:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T06:19:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T06:14:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T06:10:03Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T06:04:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T05:59:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T05:54:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T05:49:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T05:44:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T05:39:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T05:34:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T05:29:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T05:24:57Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T05:19:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T05:14:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T05:09:57Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T05:04:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T04:59:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T04:54:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T04:50:03Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T04:44:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T04:39:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T04:34:57Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T04:29:57Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T04:24:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T04:19:57Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T04:14:57Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T04:10:03Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T04:04:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T03:59:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T03:54:56Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T03:50:03Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-09-05T03:44:57Z")).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);
        Assert.assertEquals(true, iobCobCalculatorPlugin.isAbout5minData());

        iobCobCalculatorPlugin.createBucketedData();
        Assert.assertEquals(DateUtil.fromISODateString("2018-09-05T13:34:57Z").getTime(), iobCobCalculatorPlugin.getBucketedData().get(0).date);
        Assert.assertEquals(DateUtil.fromISODateString("2018-09-05T03:44:57Z").getTime(), iobCobCalculatorPlugin.getBucketedData().get(iobCobCalculatorPlugin.getBucketedData().size() - 1).date);

        // 5min 4sec data
        bgReadingList.clear();

        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T06:33:40Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T06:28:36Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T06:23:32Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T06:18:28Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T06:13:24Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T06:08:19Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T06:03:16Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T05:58:11Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T05:53:07Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T05:48:03Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T05:42:58Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T05:37:54Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T05:32:51Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T05:27:46Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T05:22:42Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T05:17:38Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T05:12:33Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T05:07:29Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T05:02:26Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T04:57:21Z")).value(100));
        bgReadingList.add(new BgReading().date(DateUtil.fromISODateString("2018-10-05T04:52:17Z")).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);

        Assert.assertEquals(false, iobCobCalculatorPlugin.isAbout5minData());
    }

    @Test
    public void getBgReadingsTest() {
        List<BgReading> bgReadingList = new ArrayList<>();

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);

        Assert.assertEquals(bgReadingList, iobCobCalculatorPlugin.getBgReadings());
    }

    @Test
    public void roundUpTimeTest() {
        Assert.assertEquals(T.mins(3).msecs(), iobCobCalculatorPlugin.roundUpTime(T.secs(155).msecs()));
    }

    @Test
    public void findNewerTest() {
        List<BgReading> bgReadingList = new ArrayList<>();

        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(15).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(10).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(5).msecs()).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);

        Assert.assertEquals(T.mins(10).msecs(), iobCobCalculatorPlugin.findNewer(T.mins(8).msecs()).date);
        Assert.assertEquals(T.mins(5).msecs(), iobCobCalculatorPlugin.findNewer(T.mins(5).msecs()).date);
        Assert.assertEquals(T.mins(10).msecs(), iobCobCalculatorPlugin.findNewer(T.mins(10).msecs()).date);
        Assert.assertEquals(T.mins(20).msecs(), iobCobCalculatorPlugin.findNewer(T.mins(20).msecs()).date);
        Assert.assertEquals(null, iobCobCalculatorPlugin.findNewer(T.mins(22).msecs()));
    }

    @Test
    public void findOlderTest() {
        List<BgReading> bgReadingList = new ArrayList<>();

        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(15).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(10).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(5).msecs()).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);

        Assert.assertEquals(T.mins(5).msecs(), iobCobCalculatorPlugin.findOlder(T.mins(8).msecs()).date);
        Assert.assertEquals(T.mins(5).msecs(), iobCobCalculatorPlugin.findOlder(T.mins(5).msecs()).date);
        Assert.assertEquals(T.mins(10).msecs(), iobCobCalculatorPlugin.findOlder(T.mins(10).msecs()).date);
        Assert.assertEquals(T.mins(20).msecs(), iobCobCalculatorPlugin.findOlder(T.mins(20).msecs()).date);
        Assert.assertEquals(null, iobCobCalculatorPlugin.findOlder(T.mins(4).msecs()));
    }

    @Test
    public void findPreviousTimeFromBucketedDataTest() {
        List<BgReading> bgReadingList = new ArrayList<>();

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);
        iobCobCalculatorPlugin.createBucketedData();
        Assert.assertEquals(null, iobCobCalculatorPlugin.findPreviousTimeFromBucketedData(1000));

        // Super data should not be touched
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(15).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(10).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(5).msecs()).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);
        iobCobCalculatorPlugin.createBucketedData();

        Assert.assertEquals(null, iobCobCalculatorPlugin.findPreviousTimeFromBucketedData(T.mins(4).msecs()));
        Assert.assertEquals((Long) T.mins(5).msecs(), iobCobCalculatorPlugin.findPreviousTimeFromBucketedData(T.mins(6).msecs()));
        Assert.assertEquals((Long) T.mins(20).msecs(), iobCobCalculatorPlugin.findPreviousTimeFromBucketedData(T.mins(20).msecs()));
        Assert.assertEquals((Long) T.mins(20).msecs(), iobCobCalculatorPlugin.findPreviousTimeFromBucketedData(T.mins(25).msecs()));
    }

    @Before
    public void doMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockL();
    }
}

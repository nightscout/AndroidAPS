package info.nightscout.androidaps.plugins.IobCobCalculatorPlugin;

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
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.utils.SP;
import info.nightscout.utils.T;

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
    public void createBucketedData5minTest() {
        List<BgReading> bgReadingList = new ArrayList<>();

        // Super data should not be touched
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(15).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(10).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(5).msecs()).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);
        iobCobCalculatorPlugin.createBucketedData();

        Assert.assertEquals(true, iobCobCalculatorPlugin.isAbout5minData());
        Assert.assertEquals(bgReadingList.get(0).date, iobCobCalculatorPlugin.getBucketedData().get(0).date);
        Assert.assertEquals(bgReadingList.get(3).date, iobCobCalculatorPlugin.getBucketedData().get(3).date);
        Assert.assertEquals(bgReadingList.size(), iobCobCalculatorPlugin.getBucketedData().size());

        // Missing value should be replaced
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(10).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(5).msecs()).value(100));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);
        iobCobCalculatorPlugin.createBucketedData();

        Assert.assertEquals(true, iobCobCalculatorPlugin.isAbout5minData());
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
        iobCobCalculatorPlugin.createBucketedData();

        Assert.assertEquals(true, iobCobCalculatorPlugin.isAbout5minData());
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
        iobCobCalculatorPlugin.createBucketedData();

        Assert.assertEquals(true, iobCobCalculatorPlugin.isAbout5minData());
        Assert.assertEquals(null, iobCobCalculatorPlugin.getBucketedData());

        // data should be reconstructed
        bgReadingList.clear();
        bgReadingList.add(new BgReading().date(T.mins(50).msecs()).value(100));
        bgReadingList.add(new BgReading().date(T.mins(45).msecs()).value(90));
        bgReadingList.add(new BgReading().date(T.mins(20).msecs()).value(40));

        iobCobCalculatorPlugin.setBgReadings(bgReadingList);
        iobCobCalculatorPlugin.createBucketedData();

        Assert.assertEquals(true, iobCobCalculatorPlugin.isAbout5minData());
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
        iobCobCalculatorPlugin.createBucketedData();

        Assert.assertEquals(false, iobCobCalculatorPlugin.isAbout5minData());
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
        Assert.assertEquals((Long)T.mins(5).msecs(), iobCobCalculatorPlugin.findPreviousTimeFromBucketedData(T.mins(6).msecs()));
        Assert.assertEquals((Long)T.mins(20).msecs(), iobCobCalculatorPlugin.findPreviousTimeFromBucketedData(T.mins(20).msecs()));
        Assert.assertEquals((Long)T.mins(20).msecs(), iobCobCalculatorPlugin.findPreviousTimeFromBucketedData(T.mins(25).msecs()));
    }

    @Before
    public void doMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockL();
    }
}

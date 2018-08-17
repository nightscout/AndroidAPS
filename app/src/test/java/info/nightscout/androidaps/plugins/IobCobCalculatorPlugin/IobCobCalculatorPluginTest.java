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
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.utils.SP;
import info.nightscout.utils.T;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, SP.class, Context.class})
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

    }

    @Before
    public void doMock() {
        AAPSMocker.mockMainApp();
    }
}

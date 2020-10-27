package info.nightscout.androidaps.utils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class DecimalFormatterTest {

    @Test
    public void to0DecimalTest() {
        Assert.assertEquals("1", DecimalFormatter.to0Decimal(1.33d).replace(",", "."));
        Assert.assertEquals("1U", DecimalFormatter.to0Decimal(1.33d, "U").replace(",", "."));
    }

    @Test
    public void to1DecimalTest() {
        Assert.assertEquals("1.3", DecimalFormatter.to1Decimal(1.33d).replace(",", "."));
        Assert.assertEquals("1.3U", DecimalFormatter.to1Decimal(1.33d, "U").replace(",", "."));
    }

    @Test
    public void to2DecimalTest() {
        Assert.assertEquals("1.33", DecimalFormatter.to2Decimal(1.3333d).replace(",", "."));
        Assert.assertEquals("1.33U", DecimalFormatter.to2Decimal(1.3333d, "U").replace(",", "."));
    }

    @Test
    public void to3DecimalTest() {
        Assert.assertEquals("1.333", DecimalFormatter.to3Decimal(1.3333d).replace(",", "."));
        Assert.assertEquals("1.333U", DecimalFormatter.to3Decimal(1.3333d, "U").replace(",", "."));
    }

    @Test
    public void toPumpSupportedBolus() {
    }

    @Test
    public void pumpSupportedBolusFormat() {
    }
}
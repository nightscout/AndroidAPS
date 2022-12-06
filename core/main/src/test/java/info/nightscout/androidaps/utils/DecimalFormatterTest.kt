package info.nightscout.androidaps.utils

import info.nightscout.interfaces.utils.DecimalFormatter
import org.junit.Assert
import org.junit.jupiter.api.Test

class DecimalFormatterTest {

    @Test fun to0DecimalTest() {
        Assert.assertEquals("1", DecimalFormatter.to0Decimal(1.33).replace(",", "."))
        Assert.assertEquals("1U", DecimalFormatter.to0Decimal(1.33, "U").replace(",", "."))
    }

    @Test fun to1DecimalTest() {
        Assert.assertEquals("1.3", DecimalFormatter.to1Decimal(1.33).replace(",", "."))
        Assert.assertEquals("1.3U", DecimalFormatter.to1Decimal(1.33, "U").replace(",", "."))
    }

    @Test fun to2DecimalTest() {
        Assert.assertEquals("1.33", DecimalFormatter.to2Decimal(1.3333).replace(",", "."))
        Assert.assertEquals("1.33U", DecimalFormatter.to2Decimal(1.3333, "U").replace(",", "."))
    }

    @Test fun to3DecimalTest() {
        Assert.assertEquals("1.333", DecimalFormatter.to3Decimal(1.3333).replace(",", "."))
        Assert.assertEquals("1.333U", DecimalFormatter.to3Decimal(1.3333, "U").replace(",", "."))
    }
}
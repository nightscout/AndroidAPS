package info.nightscout.core.utils

import info.nightscout.interfaces.utils.DecimalFormatter
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DecimalFormatterTest {

    @Test fun to0DecimalTest() {
        Assertions.assertEquals("1", DecimalFormatter.to0Decimal(1.33).replace(",", "."))
        Assertions.assertEquals("1U", DecimalFormatter.to0Decimal(1.33, "U").replace(",", "."))
    }

    @Test fun to1DecimalTest() {
        Assertions.assertEquals("1.3", DecimalFormatter.to1Decimal(1.33).replace(",", "."))
        Assertions.assertEquals("1.3U", DecimalFormatter.to1Decimal(1.33, "U").replace(",", "."))
    }

    @Test fun to2DecimalTest() {
        Assertions.assertEquals("1.33", DecimalFormatter.to2Decimal(1.3333).replace(",", "."))
        Assertions.assertEquals("1.33U", DecimalFormatter.to2Decimal(1.3333, "U").replace(",", "."))
    }

    @Test fun to3DecimalTest() {
        Assertions.assertEquals("1.333", DecimalFormatter.to3Decimal(1.3333).replace(",", "."))
        Assertions.assertEquals("1.333U", DecimalFormatter.to3Decimal(1.3333, "U").replace(",", "."))
    }
}
package info.nightscout.implementation.utils

import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class DecimalFormatterTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper

    private lateinit var sut: DecimalFormatter

    @BeforeEach
    fun setup() {
        sut = DecimalFormatterImpl(rh)
    }

    @Test fun to0DecimalTest() {
        Assertions.assertEquals("1", sut.to0Decimal(1.33).replace(",", "."))
        Assertions.assertEquals("1U", sut.to0Decimal(1.33, "U").replace(",", "."))
    }

    @Test fun to1DecimalTest() {
        Assertions.assertEquals("1.3", sut.to1Decimal(1.33).replace(",", "."))
        Assertions.assertEquals("1.3U", sut.to1Decimal(1.33, "U").replace(",", "."))
    }

    @Test fun to2DecimalTest() {
        Assertions.assertEquals("1.33", sut.to2Decimal(1.3333).replace(",", "."))
        Assertions.assertEquals("1.33U", sut.to2Decimal(1.3333, "U").replace(",", "."))
    }

    @Test fun to3DecimalTest() {
        Assertions.assertEquals("1.333", sut.to3Decimal(1.3333).replace(",", "."))
        Assertions.assertEquals("1.333U", sut.to3Decimal(1.3333, "U").replace(",", "."))
    }
}
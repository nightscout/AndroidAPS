package info.nightscout.implementation.utils

import com.google.common.truth.Truth.assertThat
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.sharedtests.TestBase
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
        assertThat(sut.to0Decimal(1.33).replace(",", ".")).isEqualTo("1")
        assertThat(sut.to0Decimal(1.33, "U").replace(",", ".")).isEqualTo("1U")
    }

    @Test fun to1DecimalTest() {
        assertThat(sut.to1Decimal(1.33).replace(",", ".")).isEqualTo("1.3")
        assertThat(sut.to1Decimal(1.33, "U").replace(",", ".")).isEqualTo("1.3U")
    }

    @Test fun to2DecimalTest() {
        assertThat(sut.to2Decimal(1.3333).replace(",", ".")).isEqualTo("1.33")
        assertThat(sut.to2Decimal(1.3333, "U").replace(",", ".")).isEqualTo("1.33U")
    }

    @Test fun to3DecimalTest() {
        assertThat(sut.to3Decimal(1.3333).replace(",", ".")).isEqualTo("1.333")
        assertThat(sut.to3Decimal(1.3333, "U").replace(",", ".")).isEqualTo("1.333U")
    }
}

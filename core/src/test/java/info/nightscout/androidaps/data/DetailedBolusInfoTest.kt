package info.nightscout.androidaps.data

import org.apache.commons.lang3.builder.EqualsBuilder
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DetailedBolusInfoTest {

    @Test fun toStringShouldBeOverloaded() {
        val detailedBolusInfo = DetailedBolusInfo()
        Assert.assertEquals(true, detailedBolusInfo.toString().contains("insulin"))
    }

    @Test fun copyShouldCopyAllProperties() {
        val d1 = DetailedBolusInfo()
        d1.deliverAt = 123
        val d2 = d1.copy()
        Assert.assertEquals(true, EqualsBuilder.reflectionEquals(d2, d1))
    }
}
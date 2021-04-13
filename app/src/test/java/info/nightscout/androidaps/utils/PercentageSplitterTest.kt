package info.nightscout.androidaps.utils

import info.nightscout.androidaps.utils.PercentageSplitter.pureName
import org.junit.Assert
import org.junit.Test

/**
 * Created by mike on 22.12.2017.
 */
class PercentageSplitterTest {

    @Test fun pureNameTestPercentageOnly() {
        Assert.assertEquals("Fiasp", pureName("Fiasp(101%)"))
    }

    @Test fun pureNameTestPercentageAndPositiveTimeShift() {
        Assert.assertEquals("Fiasp", pureName("Fiasp (101%,2h)"))
    }

    @Test fun pureNameTestPercentageAndNegtiveTimeShift() {
        Assert.assertEquals("Fiasp", pureName("Fiasp (50%,-2h)"))
    }
}
package info.nightscout.androidaps.utils

import info.nightscout.core.utils.receivers.StringUtils
import org.junit.Assert
import org.junit.jupiter.api.Test

class StringUtilsTest {

    @Test fun removeSurroundingQuotesTest() {
        var compareString = "test"
        Assert.assertEquals(compareString, StringUtils.removeSurroundingQuotes(compareString))
        Assert.assertEquals(compareString, StringUtils.removeSurroundingQuotes("\"" + compareString + "\""))
        Assert.assertEquals("\"" + compareString, StringUtils.removeSurroundingQuotes("\"" + compareString))
        compareString = "te\"st"
        Assert.assertEquals(compareString, StringUtils.removeSurroundingQuotes(compareString))
        Assert.assertEquals(compareString, StringUtils.removeSurroundingQuotes("\"" + compareString + "\""))
        Assert.assertEquals("\"" + compareString, StringUtils.removeSurroundingQuotes("\"" + compareString))
    }
}
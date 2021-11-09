package info.nightscout.androidaps.utils

import org.junit.Assert
import org.junit.Test

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
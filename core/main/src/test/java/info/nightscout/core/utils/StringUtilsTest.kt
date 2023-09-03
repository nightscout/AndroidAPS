package info.nightscout.core.utils

import info.nightscout.core.utils.receivers.StringUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class StringUtilsTest {

    @Test fun removeSurroundingQuotesTest() {
        var compareString = "test"
        Assertions.assertEquals(compareString, StringUtils.removeSurroundingQuotes(compareString))
        Assertions.assertEquals(compareString, StringUtils.removeSurroundingQuotes("\"" + compareString + "\""))
        Assertions.assertEquals("\"" + compareString, StringUtils.removeSurroundingQuotes("\"" + compareString))
        compareString = "te\"st"
        Assertions.assertEquals(compareString, StringUtils.removeSurroundingQuotes(compareString))
        Assertions.assertEquals(compareString, StringUtils.removeSurroundingQuotes("\"" + compareString + "\""))
        Assertions.assertEquals("\"" + compareString, StringUtils.removeSurroundingQuotes("\"" + compareString))
    }
}
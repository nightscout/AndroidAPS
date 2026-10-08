package app.aaps.core.utils.receivers

import kotlin.test.Test
import kotlin.test.assertEquals

class StringUtilsTest {

    @Test fun removeSurroundingQuotesTest() {
        var compareString = "test"
        assertEquals(compareString, StringUtils.removeSurroundingQuotes(compareString))
        assertEquals(compareString, StringUtils.removeSurroundingQuotes("\"" + compareString + "\""))
        assertEquals("\"" + compareString, StringUtils.removeSurroundingQuotes("\"" + compareString))
        compareString = """te"st"""
        assertEquals(compareString, StringUtils.removeSurroundingQuotes(compareString))
        assertEquals(compareString, StringUtils.removeSurroundingQuotes("\"" + compareString + "\""))
        assertEquals("\"" + compareString, StringUtils.removeSurroundingQuotes("\"" + compareString))
    }
}

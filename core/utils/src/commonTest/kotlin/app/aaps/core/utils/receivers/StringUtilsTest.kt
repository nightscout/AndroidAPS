package app.aaps.core.utils.receivers

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

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

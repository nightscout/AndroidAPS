package info.nightscout.androidaps.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StringUtilsTest {

    @Test
    public void removeSurroundingQuotesTest() {
        String compareString = "test";

        assertEquals(compareString, StringUtils.removeSurroundingQuotes(compareString));
        assertEquals(compareString, StringUtils.removeSurroundingQuotes("\"" + compareString + "\""));
        assertEquals("\"" + compareString, StringUtils.removeSurroundingQuotes("\"" + compareString));

        compareString = "te\"st";
        assertEquals(compareString, StringUtils.removeSurroundingQuotes(compareString));
        assertEquals(compareString, StringUtils.removeSurroundingQuotes("\"" + compareString + "\""));
        assertEquals("\"" + compareString, StringUtils.removeSurroundingQuotes("\"" + compareString));
    }
}

package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TimeUtilTest {

    @BeforeEach
    public void setUp() {
        DateTimeZone.setDefault(DateTimeZone.forID("Europe/Amsterdam"));
    }

    @Test
    public void testDateTimeToDuration() {
        DateTime dateTime = new DateTime(1991, 8, 13, 23, 5, 1);

        assertEquals(83101, TimeUtil.toDuration(dateTime).getStandardSeconds());
    }

    @Test
    public void testDateTimeToDurationAtDstChange() {
        DateTime dateTime = new DateTime(2020, 10, 25, 23, 5, 1);

        assertEquals(83101, TimeUtil.toDuration(dateTime).getStandardSeconds());
    }

    @Test
    public void testDateTimeToDurationAtDstReverseChange() {
        DateTime dateTime = new DateTime(2020, 3, 29, 23, 5, 1);

        assertEquals(83101, TimeUtil.toDuration(dateTime).getStandardSeconds());
    }

    @Test
    public void testDateTimeInOtherZone() {
        DateTime dateTime = new DateTime(2020, 3, 29, 23, 5, 1, DateTimeZone.forID("America/Los_Angeles"));

        assertEquals(83101, TimeUtil.toDuration(dateTime).getStandardSeconds());
    }

    @Test
    public void testDateTimeToDurationWithNullDateTime() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> TimeUtil.toDuration(null));
        assertEquals("dateTime can not be null", ex.getMessage());
    }

}
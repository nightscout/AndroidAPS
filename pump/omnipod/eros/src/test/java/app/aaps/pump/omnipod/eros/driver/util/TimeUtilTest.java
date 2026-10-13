package app.aaps.pump.omnipod.eros.driver.util;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimeUtilTest {

    @BeforeEach
    void setUp() {
        DateTimeZone.setDefault(DateTimeZone.forID("Europe/Amsterdam"));
    }

    @Test
    void testDateTimeToDuration() {
        DateTime dateTime = new DateTime(1991, 8, 13, 23, 5, 1);

        Assertions.assertEquals(83101, TimeUtil.toDuration(dateTime).getStandardSeconds());
    }

    @Test
    void testDateTimeToDurationAtDstChange() {
        DateTime dateTime = new DateTime(2020, 10, 25, 23, 5, 1);

        Assertions.assertEquals(83101, TimeUtil.toDuration(dateTime).getStandardSeconds());
    }

    @Test
    void testDateTimeToDurationAtDstReverseChange() {
        DateTime dateTime = new DateTime(2020, 3, 29, 23, 5, 1);

        Assertions.assertEquals(83101, TimeUtil.toDuration(dateTime).getStandardSeconds());
    }

    @Test
    void testDateTimeInOtherZone() {
        DateTime dateTime = new DateTime(2020, 3, 29, 23, 5, 1, DateTimeZone.forID("America/Los_Angeles"));

        Assertions.assertEquals(83101, TimeUtil.toDuration(dateTime).getStandardSeconds());
    }

    @Test
    void testDateTimeToDurationWithNullDateTime() {
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () -> TimeUtil.toDuration(null));
        Assertions.assertEquals("dateTime can not be null", ex.getMessage());
    }

}
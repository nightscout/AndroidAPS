package app.aaps.pump.omnipod.eros.driver.util;

import org.joda.time.DateTime;
import org.joda.time.Duration;

public final class TimeUtil {
    private TimeUtil() {
    }

    /**
     * @param dateTime DateTime to convert to duration
     * @return duration from the start of the day, not taking DST into account
     * (thus always having 24 hours in a day, not 23 or 25 in days where DST changes)
     */
    public static Duration toDuration(DateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("dateTime can not be null");
        }
        return new Duration(dateTime.toLocalTime().getMillisOfDay());
    }
}

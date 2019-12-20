package info.nightscout.androidaps.plugins.pump.omnipod.defs.state;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.junit.After;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.defs.FirmwareVersion;

import static org.junit.Assert.assertEquals;

// FIXME these tests fail because of some android stuff:
//  java.lang.NoClassDefFoundError: android/app/Application
//  it's caused by the PodSessionState class using the SP class for storage
public class PodSessionStateTest {

    @Test
    public void times() {
        DateTimeZone timeZone = DateTimeZone.UTC;
        DateTimeZone.setDefault(timeZone);

        DateTime now = new DateTime(2020, 1, 1, 1, 2, 3, timeZone);
        DateTime initialized = now.minus(Duration.standardDays(1));

        DateTimeUtils.setCurrentMillisFixed(now.getMillis());

        PodSessionState podSessionState = new PodSessionState(timeZone, 0x0, initialized,
                new FirmwareVersion(1, 1, 1),
                new FirmwareVersion(2, 2, 2),
                0, 0, 0, 0);

        assertEquals(initialized, podSessionState.getActivatedAt());
        assertEquals(now, podSessionState.getTime());
        assertEquals(Duration.standardHours(1).plus(Duration.standardMinutes(2).plus(Duration.standardSeconds(3))), podSessionState.getScheduleOffset());
    }

    @Test
    public void changeSystemTimeZoneWithoutChangingPodTimeZone() {
        DateTimeZone timeZone = DateTimeZone.UTC;
        DateTimeZone.setDefault(timeZone);

        DateTime now = new DateTime(2020, 1, 1, 1, 2, 3, timeZone);
        DateTime initialized = now.minus(Duration.standardDays(1));

        DateTimeUtils.setCurrentMillisFixed(now.getMillis());

        PodSessionState podSessionState = new PodSessionState(timeZone, 0x0, initialized,
                new FirmwareVersion(1, 1, 1),
                new FirmwareVersion(2, 2, 2),
                0, 0, 0, 0);

        DateTimeZone newTimeZone = DateTimeZone.forOffsetHours(2);
        DateTimeZone.setDefault(newTimeZone);

        // The system time zone has been updated, but the pod session state's time zone hasn't
        // So the pods time should not have been changed
        assertEquals(initialized, podSessionState.getActivatedAt());
        assertEquals(now, podSessionState.getTime());
        assertEquals(Duration.standardHours(1).plus(Duration.standardMinutes(2).plus(Duration.standardSeconds(3))), podSessionState.getScheduleOffset());
    }

    @Test
    public void changeSystemTimeZoneAndChangePodTimeZone() {
        DateTimeZone timeZone = DateTimeZone.UTC;
        DateTimeZone.setDefault(timeZone);

        DateTime now = new DateTime(2020, 1, 1, 1, 2, 3, timeZone);
        DateTime initialized = now.minus(Duration.standardDays(1));

        DateTimeUtils.setCurrentMillisFixed(now.getMillis());

        PodSessionState podSessionState = new PodSessionState(timeZone, 0x0, initialized,
                new FirmwareVersion(1, 1, 1),
                new FirmwareVersion(2, 2, 2),
                0, 0, 0, 0);

        DateTimeZone newTimeZone = DateTimeZone.forOffsetHours(2);
        DateTimeZone.setDefault(newTimeZone);
        podSessionState.setTimeZone(newTimeZone);

        // Both the system time zone have been updated
        // So the pods time should have been changed (to +2 hours)
        assertEquals(initialized.withZone(newTimeZone), podSessionState.getActivatedAt());
        assertEquals(now.withZone(newTimeZone), podSessionState.getTime());
        assertEquals(Duration.standardHours(3).plus(Duration.standardMinutes(2).plus(Duration.standardSeconds(3))), podSessionState.getScheduleOffset());
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }
}
package info.nightscout.androidaps.plugins.pump.omnipod.eros.manager;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.FirmwareVersion;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodProgressStatus;
import info.nightscout.androidaps.utils.rx.TestAapsSchedulers;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

import static org.junit.Assert.assertEquals;

@RunWith(PowerMockRunner.class)
public class AapsPodStateManagerTest {
    @Mock AAPSLogger aapsLogger;
    @Mock SP sp;

    private RxBusWrapper rxBus = new RxBusWrapper(new TestAapsSchedulers());

    @Test
    public void times() {
        DateTimeZone timeZone = DateTimeZone.UTC;
        DateTimeZone.setDefault(timeZone);

        DateTime now = new DateTime(2020, 1, 1, 1, 2, 3, timeZone);

        DateTimeUtils.setCurrentMillisFixed(now.getMillis());

        AapsPodStateManager podStateManager = new AapsPodStateManager(aapsLogger, sp, rxBus);
        podStateManager.initState(0x0);
        podStateManager.setInitializationParameters(0, 0, new FirmwareVersion(1, 1, 1),
                new FirmwareVersion(2, 2, 2), timeZone, PodProgressStatus.ABOVE_FIFTY_UNITS);

        assertEquals(now, podStateManager.getTime());
        assertEquals(Duration.standardHours(1).plus(Duration.standardMinutes(2).plus(Duration.standardSeconds(3))), podStateManager.getScheduleOffset());
    }

    @Test
    public void changeSystemTimeZoneWithoutChangingPodTimeZone() {
        DateTimeZone timeZone = DateTimeZone.UTC;
        DateTimeZone.setDefault(timeZone);

        DateTime now = new DateTime(2020, 1, 1, 1, 2, 3, timeZone);

        DateTimeUtils.setCurrentMillisFixed(now.getMillis());

        AapsPodStateManager podStateManager = new AapsPodStateManager(aapsLogger, sp, rxBus);
        podStateManager.initState(0x0);
        podStateManager.setInitializationParameters(0, 0, new FirmwareVersion(1, 1, 1),
                new FirmwareVersion(2, 2, 2), timeZone, PodProgressStatus.ABOVE_FIFTY_UNITS);

        DateTimeZone newTimeZone = DateTimeZone.forOffsetHours(2);
        DateTimeZone.setDefault(newTimeZone);

        // The system time zone has been updated, but the pod session state's time zone hasn't
        // So the pods time should not have been changed
        assertEquals(now, podStateManager.getTime());
        assertEquals(Duration.standardHours(1).plus(Duration.standardMinutes(2).plus(Duration.standardSeconds(3))), podStateManager.getScheduleOffset());
    }

    @Test
    public void changeSystemTimeZoneAndChangePodTimeZone() {
        DateTimeZone timeZone = DateTimeZone.UTC;
        DateTimeZone.setDefault(timeZone);

        DateTime now = new DateTime(2020, 1, 1, 1, 2, 3, timeZone);

        DateTimeUtils.setCurrentMillisFixed(now.getMillis());

        AapsPodStateManager podStateManager = new AapsPodStateManager(aapsLogger, sp, rxBus);
        podStateManager.initState(0x0);
        podStateManager.setInitializationParameters(0, 0, new FirmwareVersion(1, 1, 1),
                new FirmwareVersion(2, 2, 2), timeZone, PodProgressStatus.ABOVE_FIFTY_UNITS);

        DateTimeZone newTimeZone = DateTimeZone.forOffsetHours(2);
        DateTimeZone.setDefault(newTimeZone);
        podStateManager.setTimeZone(newTimeZone);

        // Both the system time zone have been updated
        // So the pods time should have been changed (to +2 hours)
        assertEquals(now.withZone(newTimeZone), podStateManager.getTime());
        assertEquals(Duration.standardHours(3).plus(Duration.standardMinutes(2).plus(Duration.standardSeconds(3))), podStateManager.getScheduleOffset());
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }
}
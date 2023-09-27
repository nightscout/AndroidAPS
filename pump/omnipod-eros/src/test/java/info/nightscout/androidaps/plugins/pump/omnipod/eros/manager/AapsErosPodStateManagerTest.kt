package info.nightscout.androidaps.plugins.pump.omnipod.eros.manager

import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.shared.tests.TestBase
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.FirmwareVersion
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodProgressStatus
import org.joda.time.DateTime
import org.joda.time.DateTimeUtils
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock

class AapsErosPodStateManagerTest : TestBase() {

    @Mock lateinit var sp: SP

    @Test fun times() {
        val timeZone = DateTimeZone.UTC
        DateTimeZone.setDefault(timeZone)
        val now = DateTime(2020, 1, 1, 1, 2, 3, timeZone)
        DateTimeUtils.setCurrentMillisFixed(now.millis)
        val podStateManager = AapsErosPodStateManager(aapsLogger, sp, rxBus)
        podStateManager.initState(0x01)
        podStateManager.setInitializationParameters(
            0, 0, FirmwareVersion(1, 1, 1),
            FirmwareVersion(2, 2, 2), timeZone, PodProgressStatus.ABOVE_FIFTY_UNITS
        )
        Assertions.assertEquals(now, podStateManager.time)
        Assertions.assertEquals(
            Duration.standardHours(1)
                .plus(Duration.standardMinutes(2).plus(Duration.standardSeconds(3))),
            podStateManager.scheduleOffset
        )
    }

    @Test fun changeSystemTimeZoneWithoutChangingPodTimeZone() {
        val timeZone = DateTimeZone.UTC
        DateTimeZone.setDefault(timeZone)
        val now = DateTime(2020, 1, 1, 1, 2, 3, timeZone)
        DateTimeUtils.setCurrentMillisFixed(now.millis)
        val podStateManager = AapsErosPodStateManager(aapsLogger, sp, rxBus)
        podStateManager.initState(0x01)
        podStateManager.setInitializationParameters(
            0, 0, FirmwareVersion(1, 1, 1),
            FirmwareVersion(2, 2, 2), timeZone, PodProgressStatus.ABOVE_FIFTY_UNITS
        )
        val newTimeZone = DateTimeZone.forOffsetHours(2)
        DateTimeZone.setDefault(newTimeZone)

        // The system time zone has been updated, but the pod session state's time zone hasn't
        // So the pods time should not have been changed
        Assertions.assertEquals(now, podStateManager.time)
        Assertions.assertEquals(
            Duration.standardHours(1)
                .plus(Duration.standardMinutes(2).plus(Duration.standardSeconds(3))),
            podStateManager.scheduleOffset
        )
    }

    @Test fun changeSystemTimeZoneAndChangePodTimeZone() {
        val timeZone = DateTimeZone.UTC
        DateTimeZone.setDefault(timeZone)
        val now = DateTime(2020, 1, 1, 1, 2, 3, timeZone)
        DateTimeUtils.setCurrentMillisFixed(now.millis)
        val podStateManager = AapsErosPodStateManager(aapsLogger, sp, rxBus)
        podStateManager.initState(0x01)
        podStateManager.setInitializationParameters(
            0, 0, FirmwareVersion(1, 1, 1),
            FirmwareVersion(2, 2, 2), timeZone, PodProgressStatus.ABOVE_FIFTY_UNITS
        )
        val newTimeZone = DateTimeZone.forOffsetHours(2)
        DateTimeZone.setDefault(newTimeZone)
        podStateManager.timeZone = newTimeZone

        // Both the system time zone have been updated
        // So the pods time should have been changed (to +2 hours)
        Assertions.assertEquals(now.withZone(newTimeZone), podStateManager.time)
        Assertions.assertEquals(
            Duration.standardHours(3)
                .plus(Duration.standardMinutes(2).plus(Duration.standardSeconds(3))),
            podStateManager.scheduleOffset
        )
    }

    @AfterEach fun tearDown() {
        DateTimeUtils.setCurrentMillisSystem()
    }
}
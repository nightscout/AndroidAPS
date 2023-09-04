package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.podinfo;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.FaultEventCode;
import info.nightscout.pump.core.utils.ByteUtil;

class PodInfoFaultAndInitializationTimeTest {
    @Test
    void testDecoding() {
        PodInfoFaultAndInitializationTime podInfoFaultAndInitializationTime = new PodInfoFaultAndInitializationTime(ByteUtil.fromHexString("059200010000000000000000091912170e")); // From https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/PodInfoTests.swift

        Assertions.assertEquals(FaultEventCode.BAD_PUMP_REQ_2_STATE, podInfoFaultAndInitializationTime.getFaultEventCode());
        Assertions.assertTrue(Duration.standardMinutes(1).isEqual(podInfoFaultAndInitializationTime.getTimeFaultEvent()));

        DateTime dateTime = podInfoFaultAndInitializationTime.getInitializationTime();
        Assertions.assertEquals(2018, dateTime.getYear());
        Assertions.assertEquals(9, dateTime.getMonthOfYear());
        Assertions.assertEquals(25, dateTime.getDayOfMonth());
        Assertions.assertEquals(23, dateTime.getHourOfDay());
        Assertions.assertEquals(14, dateTime.getMinuteOfHour());
    }
}

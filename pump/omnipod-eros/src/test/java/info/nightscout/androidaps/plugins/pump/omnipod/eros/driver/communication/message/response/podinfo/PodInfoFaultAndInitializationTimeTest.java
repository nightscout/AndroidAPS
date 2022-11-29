package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.podinfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.FaultEventCode;
import info.nightscout.pump.core.utils.ByteUtil;

public class PodInfoFaultAndInitializationTimeTest {
    @Test
    public void testDecoding() {
        PodInfoFaultAndInitializationTime podInfoFaultAndInitializationTime = new PodInfoFaultAndInitializationTime(ByteUtil.fromHexString("059200010000000000000000091912170e")); // From https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/PodInfoTests.swift

        assertEquals(FaultEventCode.BAD_PUMP_REQ_2_STATE, podInfoFaultAndInitializationTime.getFaultEventCode());
        assertTrue(Duration.standardMinutes(1).isEqual(podInfoFaultAndInitializationTime.getTimeFaultEvent()));

        DateTime dateTime = podInfoFaultAndInitializationTime.getInitializationTime();
        Assert.assertEquals(2018, dateTime.getYear());
        Assert.assertEquals(9, dateTime.getMonthOfYear());
        Assert.assertEquals(25, dateTime.getDayOfMonth());
        Assert.assertEquals(23, dateTime.getHourOfDay());
        Assert.assertEquals(14, dateTime.getMinuteOfHour());
    }
}

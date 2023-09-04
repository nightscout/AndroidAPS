package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.podinfo;

import org.joda.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.FaultEventCode;
import info.nightscout.pump.core.utils.ByteUtil;

class PodInfoDataLogTest {
    @Test
    void testDecoding() {
        PodInfoDataLog podInfoDataLog = new PodInfoDataLog(ByteUtil.fromHexString("030100010001043c"), 8); // From https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/PodInfoTests.swift

        Assertions.assertEquals(FaultEventCode.FAILED_FLASH_ERASE, podInfoDataLog.getFaultEventCode());
        Assertions.assertTrue(Duration.standardMinutes(1).isEqual(podInfoDataLog.getTimeFaultEvent()));
        Assertions.assertTrue(Duration.standardMinutes(1).isEqual(podInfoDataLog.getTimeSinceActivation()));
        Assertions.assertEquals(4, podInfoDataLog.getDataChunkSize());
        Assertions.assertEquals(60, podInfoDataLog.getMaximumNumberOfDwords());
    }
}

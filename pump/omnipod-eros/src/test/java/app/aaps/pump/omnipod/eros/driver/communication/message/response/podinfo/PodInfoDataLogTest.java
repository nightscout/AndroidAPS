package app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo;

import static com.google.common.truth.Truth.assertThat;

import org.joda.time.Duration;
import org.junit.jupiter.api.Test;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.definition.FaultEventCode;

class PodInfoDataLogTest {
    @Test
    void testDecoding() {
        PodInfoDataLog podInfoDataLog = new PodInfoDataLog(ByteUtil.INSTANCE.fromHexString("030100010001043c"), 8); // From https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/PodInfoTests.swift

        assertThat(podInfoDataLog.getFaultEventCode()).isEqualTo(FaultEventCode.FAILED_FLASH_ERASE);
        assertThat(podInfoDataLog.getTimeFaultEvent()).isEqualTo(Duration.standardMinutes(1));
        assertThat(podInfoDataLog.getTimeSinceActivation()).isEqualTo(Duration.standardMinutes(1));
        assertThat(podInfoDataLog.getDataChunkSize()).isEqualTo(4);
        assertThat(podInfoDataLog.getMaximumNumberOfDwords()).isEqualTo(60);
    }
}

package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodInfoType;
import info.nightscout.pump.core.utils.ByteUtil;

class GetStatusCommandTest {
    @Test
    void testPodInfoTypeNormal() {
        GetStatusCommand getStatusCommand = new GetStatusCommand(PodInfoType.NORMAL);

        Assertions.assertArrayEquals(ByteUtil.fromHexString("0e0100"), getStatusCommand.getRawData());
    }

    @Test
    void testPodInfoTypeConfiguredAlerts() {
        GetStatusCommand getStatusCommand = new GetStatusCommand(PodInfoType.ACTIVE_ALERTS);

        Assertions.assertArrayEquals(ByteUtil.fromHexString("0e0101"), getStatusCommand.getRawData());
    }

    @Test
    void testPodInfoTypeFaultEvents() {
        GetStatusCommand getStatusCommand = new GetStatusCommand(PodInfoType.DETAILED_STATUS);

        Assertions.assertArrayEquals(ByteUtil.fromHexString("0e0102"), getStatusCommand.getRawData());
    }
}

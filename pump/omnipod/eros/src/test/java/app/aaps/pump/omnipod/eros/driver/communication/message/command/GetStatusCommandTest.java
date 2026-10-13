package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.definition.PodInfoType;

class GetStatusCommandTest {
    @Test
    void testPodInfoTypeNormal() {
        GetStatusCommand getStatusCommand = new GetStatusCommand(PodInfoType.NORMAL);

        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("0e0100"), getStatusCommand.getRawData());
    }

    @Test
    void testPodInfoTypeConfiguredAlerts() {
        GetStatusCommand getStatusCommand = new GetStatusCommand(PodInfoType.ACTIVE_ALERTS);

        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("0e0101"), getStatusCommand.getRawData());
    }

    @Test
    void testPodInfoTypeFaultEvents() {
        GetStatusCommand getStatusCommand = new GetStatusCommand(PodInfoType.DETAILED_STATUS);

        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("0e0102"), getStatusCommand.getRawData());
    }
}

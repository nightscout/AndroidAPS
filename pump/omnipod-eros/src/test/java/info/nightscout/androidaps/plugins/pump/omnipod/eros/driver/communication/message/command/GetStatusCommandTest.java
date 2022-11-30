package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command;

import static org.junit.Assert.assertArrayEquals;

import org.junit.jupiter.api.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodInfoType;
import info.nightscout.pump.core.utils.ByteUtil;

public class GetStatusCommandTest {
    @Test
    public void testPodInfoTypeNormal() {
        GetStatusCommand getStatusCommand = new GetStatusCommand(PodInfoType.NORMAL);

        assertArrayEquals(ByteUtil.fromHexString("0e0100"), getStatusCommand.getRawData());
    }

    @Test
    public void testPodInfoTypeConfiguredAlerts() {
        GetStatusCommand getStatusCommand = new GetStatusCommand(PodInfoType.ACTIVE_ALERTS);

        assertArrayEquals(ByteUtil.fromHexString("0e0101"), getStatusCommand.getRawData());
    }

    @Test
    public void testPodInfoTypeFaultEvents() {
        GetStatusCommand getStatusCommand = new GetStatusCommand(PodInfoType.DETAILED_STATUS);

        assertArrayEquals(ByteUtil.fromHexString("0e0102"), getStatusCommand.getRawData());
    }
}

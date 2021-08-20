package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command;

import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodInfoType;

import static org.junit.Assert.assertArrayEquals;

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

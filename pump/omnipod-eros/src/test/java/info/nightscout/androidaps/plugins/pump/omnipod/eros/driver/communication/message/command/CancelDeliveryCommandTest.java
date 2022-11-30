package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command;

import static org.junit.Assert.assertArrayEquals;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.BeepType;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.DeliveryType;
import info.nightscout.pump.core.utils.ByteUtil;

public class CancelDeliveryCommandTest {

    @Test
    public void testCancelBolusAndBasalWithBeep() {
        CancelDeliveryCommand command = new CancelDeliveryCommand(0x10203040, BeepType.BIP_BIP, EnumSet.of(DeliveryType.BASAL, DeliveryType.BOLUS));

        byte[] expected = ByteUtil.fromHexString("1F051020304035");
        assertArrayEquals(expected, command.getRawData());
    }

    @Test
    public void testCancelBolusWithBeep() {
        CancelDeliveryCommand command = new CancelDeliveryCommand(0x4d91f8ff, BeepType.BEEEEEEP, DeliveryType.BOLUS);

        byte[] expected = ByteUtil.fromHexString("1f054d91f8ff64");
        assertArrayEquals(expected, command.getRawData());
    }

    @Test
    public void testSuspendBasalCommandWithoutBeep() {
        CancelDeliveryCommand command = new CancelDeliveryCommand(0x6fede14a, BeepType.NO_BEEP, DeliveryType.BASAL);

        byte[] expected = ByteUtil.fromHexString("1f056fede14a01");
        assertArrayEquals(expected, command.getRawData());
    }


    @Test
    public void testCancelTempBasalWithoutBeep() {
        CancelDeliveryCommand cancelDeliveryCommand = new CancelDeliveryCommand(0xf76d34c4, BeepType.NO_BEEP, DeliveryType.TEMP_BASAL);
        assertArrayEquals(ByteUtil.fromHexString("1f05f76d34c402"), cancelDeliveryCommand.getRawData());
    }
}

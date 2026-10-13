package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.definition.BeepType;
import app.aaps.pump.omnipod.eros.driver.definition.DeliveryType;

/**
 * @noinspection SpellCheckingInspection
 */
class CancelDeliveryCommandTest {

    @Test
    void testCancelBolusAndBasalWithBeep() {
        CancelDeliveryCommand command = new CancelDeliveryCommand(0x10203040, BeepType.BIP_BIP, EnumSet.of(DeliveryType.BASAL, DeliveryType.BOLUS));

        byte[] expected = ByteUtil.INSTANCE.fromHexString("1F051020304035");
        Assertions.assertArrayEquals(expected, command.getRawData());
    }

    @Test
    void testCancelBolusWithBeep() {
        CancelDeliveryCommand command = new CancelDeliveryCommand(0x4d91f8ff, BeepType.BEEEEEEP, DeliveryType.BOLUS);

        byte[] expected = ByteUtil.INSTANCE.fromHexString("1f054d91f8ff64");
        Assertions.assertArrayEquals(expected, command.getRawData());
    }

    @Test
    void testSuspendBasalCommandWithoutBeep() {
        CancelDeliveryCommand command = new CancelDeliveryCommand(0x6fede14a, BeepType.NO_BEEP, DeliveryType.BASAL);

        byte[] expected = ByteUtil.INSTANCE.fromHexString("1f056fede14a01");
        Assertions.assertArrayEquals(expected, command.getRawData());
    }


    @Test
    void testCancelTempBasalWithoutBeep() {
        CancelDeliveryCommand cancelDeliveryCommand = new CancelDeliveryCommand(0xf76d34c4, BeepType.NO_BEEP, DeliveryType.TEMP_BASAL);
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("1f05f76d34c402"), cancelDeliveryCommand.getRawData());
    }
}

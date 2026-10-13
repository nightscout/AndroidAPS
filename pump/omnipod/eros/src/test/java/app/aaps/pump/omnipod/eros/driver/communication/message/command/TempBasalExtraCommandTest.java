package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import org.joda.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.aaps.core.utils.pump.ByteUtil;

class TempBasalExtraCommandTest {
    @Test
    void testTempBasalExtraCommand() {
        TempBasalExtraCommand tempBasalExtraCommand = new TempBasalExtraCommand(30D, Duration.standardMinutes(30),
                false, true, Duration.standardHours(1));
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("160e7c000bb8000927c00bb8000927c0"),
                tempBasalExtraCommand.getRawData());
    }

    @Test
    void testBasalExtraCommandForOddPulseCountRate() {
        TempBasalExtraCommand tempBasalExtraCommand = new TempBasalExtraCommand(0.05, Duration.standardMinutes(30),
                false, true, Duration.standardHours(1));
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("160e7c00000515752a00000515752a00"),
                tempBasalExtraCommand.getRawData());

        TempBasalExtraCommand tempBasalExtraCommand2 = new TempBasalExtraCommand(2.05, Duration.standardMinutes(30),
                false, false, Duration.standardHours(1));
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("160e3c0000cd0085fac700cd0085fac7"),
                tempBasalExtraCommand2.getRawData());

        TempBasalExtraCommand tempBasalExtraCommand3 = new TempBasalExtraCommand(2.10, Duration.standardMinutes(30),
                false, false, Duration.standardHours(1));
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("160e3c0000d20082ca2400d20082ca24"),
                tempBasalExtraCommand3.getRawData());

        TempBasalExtraCommand tempBasalExtraCommand4 = new TempBasalExtraCommand(2.15, Duration.standardMinutes(30),
                false, false, Duration.standardHours(1));
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("160e3c0000d7007fbf7d00d7007fbf7d"),
                tempBasalExtraCommand4.getRawData());
    }

    @Test
    void testBasalExtraCommandPulseCount() {
        TempBasalExtraCommand tempBasalExtraCommand = new TempBasalExtraCommand(27.35, Duration.standardHours(12),
                false, false, Duration.ZERO);
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("16140000f5b9000a0ad7f5b9000a0ad70aaf000a0ad7"),
                tempBasalExtraCommand.getRawData());
    }

    @Test
    void testTempBasalExtraCommandExtremeValues() {
        TempBasalExtraCommand tempBasalExtraCommand2 = new TempBasalExtraCommand(29.95, Duration.standardHours(12),
                false, false, Duration.standardHours(1));
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("16143c00f5af00092ba9f5af00092ba9231900092ba9"),
                tempBasalExtraCommand2.getRawData());

        TempBasalExtraCommand tempBasalExtraCommand = new TempBasalExtraCommand(30D, Duration.standardHours(12),
                false, false, Duration.standardHours(1));
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("16143c00f618000927c0f618000927c02328000927c0"),
                tempBasalExtraCommand.getRawData());
    }

    @Test
    void testTempBasalExtraCommandZeroBasal() {
        TempBasalExtraCommand tempBasalExtraCommand = new TempBasalExtraCommand(0D, Duration.standardMinutes(30),
                false, true, Duration.standardHours(1));
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("160e7c0000006b49d20000006b49d200"),
                tempBasalExtraCommand.getRawData());

        TempBasalExtraCommand tempBasalExtraCommand2 = new TempBasalExtraCommand(0D, Duration.standardHours(3),
                false, true, Duration.standardHours(1));
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("162c7c0000006b49d20000006b49d20000006b49d20000006b49d20000006b49d20000006b49d20000006b49d200"),
                tempBasalExtraCommand2.getRawData());
    }
}

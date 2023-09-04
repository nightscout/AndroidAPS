package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command;

import org.joda.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.BeepConfigType;
import info.nightscout.pump.core.utils.ByteUtil;

class BeepConfigCommandTest {
    @Test
    void testConfidenceReminders() {
        BeepConfigCommand beepConfigCommand = new BeepConfigCommand(BeepConfigType.BIP_BEEP_BIP_BEEP_BIP_BEEP_BIP_BEEP, true,
                Duration.ZERO, true, Duration.ZERO,
                true, Duration.ZERO);
        Assertions.assertArrayEquals(ByteUtil.fromHexString("1e0402404040"), beepConfigCommand.getRawData());
    }

    @Test
    void testProgramReminders() {
        BeepConfigCommand beepConfigCommand = new BeepConfigCommand(BeepConfigType.NO_BEEP, true,
                Duration.ZERO, false, Duration.standardMinutes(60),
                false, Duration.standardMinutes(60));
        Assertions.assertArrayEquals(ByteUtil.fromHexString("1e040f403c3c"), beepConfigCommand.getRawData());
    }
}

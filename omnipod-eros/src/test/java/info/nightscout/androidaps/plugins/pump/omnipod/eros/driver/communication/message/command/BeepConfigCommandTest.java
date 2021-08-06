package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command;

import org.joda.time.Duration;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.BeepConfigType;

import static org.junit.Assert.assertArrayEquals;

public class BeepConfigCommandTest {
    @Test
    public void testConfidenceReminders() {
        BeepConfigCommand beepConfigCommand = new BeepConfigCommand(BeepConfigType.BIP_BEEP_BIP_BEEP_BIP_BEEP_BIP_BEEP, true,
                Duration.ZERO, true, Duration.ZERO,
                true, Duration.ZERO);
        assertArrayEquals(ByteUtil.fromHexString("1e0402404040"), beepConfigCommand.getRawData());
    }

    @Test
    public void testProgramReminders() {
        BeepConfigCommand beepConfigCommand = new BeepConfigCommand(BeepConfigType.NO_BEEP, true,
                Duration.ZERO, false, Duration.standardMinutes(60),
                false, Duration.standardMinutes(60));
        assertArrayEquals(ByteUtil.fromHexString("1e040f403c3c"), beepConfigCommand.getRawData());
    }
}

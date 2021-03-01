package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ProgramReminder;

import static org.junit.Assert.assertArrayEquals;

public class ProgramBolusCommandTest {
    @Test
    public void testProgramBolusCommand() throws DecoderException {
        byte[] encoded = new ProgramBolusCommand.Builder() //
                .setNumberOfUnits(5) //
                .setProgramReminder(new ProgramReminder(false, true, (byte) 0)) //
                .setDelayBetweenPulsesInEighthSeconds((byte) 16) //
                .setUniqueId(37879809) //
                .setSequenceNumber((short) 14) //
                .setNonce(1229869870) //
                .build() //
                .getEncoded();

        assertArrayEquals(Hex.decodeHex("02420001381F1A0E494E532E02010F01064000640064170D4003E800030D4000000000000080F6"), encoded);
    }

}
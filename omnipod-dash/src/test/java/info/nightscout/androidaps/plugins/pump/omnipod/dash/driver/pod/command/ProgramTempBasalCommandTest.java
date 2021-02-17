package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ProgramReminder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ProgramTempBasalCommandTest {
    @Test
    public void testFirstTempBasalMethod() throws DecoderException {
        ProgramTempBasalCommand command = new ProgramTempBasalCommand.Builder() //
                .setUniqueId(37879809) //
                .setNonce(1229869870) //
                .setSequenceNumber((short) 3) //
                .setRateInUnitsPerHour(5d) //
                .setDurationInMinutes((short) 60) //
                .setProgramReminder(new ProgramReminder(false, true, (byte) 0)) //
                .build();

        assertEquals(ProgramTempBasalCommand.TempBasalMethod.SECOND_METHOD, command.getTempBasalMethod());

        assertArrayEquals(Hex.decodeHex("024200010C201A0E494E532E01014303384000322032160E400005DC0036EE8005DC0036EE808396"), command.getEncoded());
    }

    @Test
    public void testSecondTempBasalMethod() throws DecoderException {
        ProgramTempBasalCommand command = new ProgramTempBasalCommand.Builder() //
                .setUniqueId(37879809) //
                .setNonce(1229869870) //
                .setSequenceNumber((short) 13) //
                .setRateInUnitsPerHour(0.0) //
                .setDurationInMinutes((short) 60) //
                .setProgramReminder(new ProgramReminder(true, true, (byte) 0)) //
                .build();

        assertEquals(ProgramTempBasalCommand.TempBasalMethod.SECOND_METHOD, command.getTempBasalMethod());

        assertArrayEquals(Hex.decodeHex("0242000134201A0E494E532E01007B03384000002000160EC00000036B49D2000003EB49D2000223"), command.getEncoded());
    }
}
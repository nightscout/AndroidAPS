package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ProgramReminder;

import static org.junit.Assert.assertArrayEquals;

public class ProgramTempBasalCommandTest {
    @Test
    public void testExtraAlternateSegmentPulseTempBasal() throws DecoderException {
        ProgramTempBasalCommand command = new ProgramTempBasalCommand.Builder() //
                .setUniqueId(37879809) //
                .setNonce(1229869870) //
                .setSequenceNumber((short) 15) //
                .setRateInUnitsPerHour(5.05d) //
                .setDurationInMinutes((short) 60) //
                .setProgramReminder(new ProgramReminder(false, true, (byte) 0)) //
                .build();

        assertArrayEquals(Hex.decodeHex("024200013C201A0E494E532E01011102384000321832160E400003F20036634403F20036634482A6"), command.getEncoded());
    }

    @Test
    public void testZeroTempBasal() throws DecoderException {
        ProgramTempBasalCommand command = new ProgramTempBasalCommand.Builder() //
                .setUniqueId(37879809) //
                .setNonce(1229869870) //
                .setSequenceNumber((short) 7) //
                .setRateInUnitsPerHour(0.0) //
                .setDurationInMinutes((short) 300) //
                .setProgramReminder(new ProgramReminder(true, true, (byte) 0)) //
                .build();

        assertArrayEquals(Hex.decodeHex("024200011C201A0E494E532E0100820A384000009000160EC000000A6B49D200000AEB49D20001DE"), command.getEncoded());
    }
}
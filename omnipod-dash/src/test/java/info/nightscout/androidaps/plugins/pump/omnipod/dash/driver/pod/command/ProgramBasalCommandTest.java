package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ProgramReminder;

import static org.junit.Assert.assertArrayEquals;

public class ProgramBasalCommandTest {
    @Test
    public void testProgramBasalCommand() throws DecoderException {
        List<BasalProgram.Segment> segments = Arrays.asList(
                new BasalProgram.Segment((short) 0, (short) 48, 300)
        );
        BasalProgram basalProgram = new BasalProgram(segments);
        Date date = new Date(2021, 1, 17, 14, 47, 43);

        byte[] encoded = new ProgramBasalCommand.Builder() //
                .setUniqueId(37879809) //
                .setNonce(1229869870) //
                .setSequenceNumber((short) 10) //
                .setBasalProgram(basalProgram) //
                .setCurrentTime(date) //
                .setProgramReminder(new ProgramReminder(false, true, (byte) 0)) //
                .build() //
                .getEncoded();

        assertArrayEquals(Hex.decodeHex("0242000128241A12494E532E0005E81D1708000CF01EF01EF01E130E40001593004C4B403840005B8D80827C"), encoded);
    }

}
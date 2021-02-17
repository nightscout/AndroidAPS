package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class DeactivateCommandTest {
    @Test
    public void testEncoding() throws DecoderException {
        byte[] encoded = new DeactivateCommand.Builder() //
                .setUniqueId(37879809) //
                .setSequenceNumber((short) 5) //
                .setNonce(1229869870) //
                .build() //
                .getEncoded();

        assertArrayEquals(Hex.decodeHex("0242000114061C04494E532E001C"), encoded);
    }
}
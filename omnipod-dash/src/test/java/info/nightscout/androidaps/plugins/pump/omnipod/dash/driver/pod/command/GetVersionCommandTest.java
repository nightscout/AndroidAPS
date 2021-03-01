package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class GetVersionCommandTest {
    @Test
    public void testEncoding() throws DecoderException {
        byte[] encoded = new GetVersionCommand.Builder() //
                .setSequenceNumber((short) 0) //
                .setUniqueId(GetVersionCommand.DEFAULT_UNIQUE_ID) //
                .build() //
                .getEncoded();

        assertArrayEquals(Hex.decodeHex("FFFFFFFF00060704FFFFFFFF82B2"), encoded);
    }
}
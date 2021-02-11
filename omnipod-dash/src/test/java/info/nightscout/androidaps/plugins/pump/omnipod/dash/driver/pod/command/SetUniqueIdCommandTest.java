package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertArrayEquals;

public class SetUniqueIdCommandTest {
    @Test
    public void testEncoding() throws DecoderException {
        byte[] encoded = new SetUniqueIdCommand(37879811, (short) 6, 135556289, 681767, new Date(2021, 1, 10, 14, 41), false) //
                .getEncoded();

        assertArrayEquals(Hex.decodeHex("FFFFFFFF18150313024200031404020A150E2908146CC1000A67278344"), encoded);
    }
}
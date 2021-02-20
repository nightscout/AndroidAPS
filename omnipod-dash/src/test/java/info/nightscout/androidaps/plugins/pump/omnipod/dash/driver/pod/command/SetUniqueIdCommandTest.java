package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertArrayEquals;

public class SetUniqueIdCommandTest {
    @Test
    public void testEncoding() throws DecoderException {
        @SuppressWarnings("deprecation")
        byte[] encoded = new SetUniqueIdCommand.Builder() //
                .setUniqueId(37879811) //
                .setSequenceNumber((short) 6) //
                .setLotNumber(135556289) //
                .setPodSequenceNumber(681767) //
                .setInitializationTime(new Date(2021, 1, 10, 14, 41)) //
                .build() //
                .getEncoded();

        assertArrayEquals(Hex.decodeHex("FFFFFFFF18150313024200031404020A150E2908146CC1000A67278344"), encoded);
    }
}
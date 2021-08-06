package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class SilenceAlertsCommandTest {
    @Test
    public void testSilenceLowReservoirAlert() throws DecoderException {
        byte[] encoded = new SilenceAlertsCommand.Builder() //
                .setUniqueId(37879811) //
                .setSequenceNumber((short) 1) //
                .setNonce(1229869870) //
                .setSilenceLowReservoirAlert(true) //
                .build() //
                .getEncoded();

        assertArrayEquals(Hex.decodeHex("0242000304071105494E532E1081CE"), encoded);
    }

    // TODO capture more silence alerts commands
}
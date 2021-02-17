package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BeepType;

import static org.junit.Assert.assertArrayEquals;

public class StopDeliveryCommandTest {
    @Test
    public void testStopTempBasal() throws DecoderException {
        byte[] encoded = new StopDeliveryCommand.Builder() //
                .setUniqueId(37879811) //
                .setSequenceNumber((short) 0) //
                .setNonce(1229869870) //
                .setDeliveryType(StopDeliveryCommand.DeliveryType.TEMP_BASAL) //
                .setBeepType(BeepType.LONG_SINGLE_BEEP) //
                .build() //
                .getEncoded();

        assertArrayEquals(Hex.decodeHex("0242000300071F05494E532E6201B1"), encoded);
    }

    @Test
    public void testSuspendDelivery() throws DecoderException {
        byte[] encoded = new StopDeliveryCommand.Builder() //
                .setUniqueId(37879811) //
                .setSequenceNumber((short) 2) //
                .setNonce(1229869870) //
                .setDeliveryType(StopDeliveryCommand.DeliveryType.ALL) //
                .setBeepType(BeepType.SILENT) //
                .build() //
                .getEncoded();

        assertArrayEquals(Hex.decodeHex("0242000308071F05494E532E078287"), encoded);
    }

    // TODO test cancel bolus
}
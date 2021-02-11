package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BeepType;

import static org.junit.Assert.assertArrayEquals;

public class StopDeliveryCommandTest {
    @Test
    public void testStopTempBasal() throws DecoderException {
        byte[] encoded = new StopDeliveryCommand(37879811, (short) 0, false, StopDeliveryCommand.DeliveryType.TEMP_BASAL, BeepType.LONG_SINGLE_BEEP) //
                .getEncoded();

        assertArrayEquals(Hex.decodeHex("0242000300071F05494E532E6201B1"), encoded);
    }

    @Test
    public void testSuspendDelivery() throws DecoderException {
        byte[] encoded = new StopDeliveryCommand(37879811, (short) 2, false, StopDeliveryCommand.DeliveryType.ALL, BeepType.SILENT) //
                .getEncoded();

        assertArrayEquals(Hex.decodeHex("0242000308071F05494E532E078287"), encoded);
    }

    // TODO test cancel bolus
}
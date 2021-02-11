package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertTriggerType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BeepRepetitionType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BeepType;

import static org.junit.Assert.assertArrayEquals;

public class ProgramAlertsCommandTest {
    @Test
    public void testExpirationAlerts() throws DecoderException {
        List<AlertConfiguration> configurations = new ArrayList<>();
        configurations.add(new AlertConfiguration(AlertSlot.EXPIRATION, true, (short) 420, false, AlertTriggerType.TIME_TRIGGER, (short) 4305, BeepType.FOUR_TIMES_BIP_BEEP, BeepRepetitionType.XXX3));
        configurations.add(new AlertConfiguration(AlertSlot.EXPIRATION_IMMINENT, true, (short) 0, false, AlertTriggerType.TIME_TRIGGER, (short) 4725, BeepType.FOUR_TIMES_BIP_BEEP, BeepRepetitionType.XXX4));

        byte[] encoded = new ProgramAlertsCommand(37879811, (short) 3, true, configurations).getEncoded();

        assertArrayEquals(Hex.decodeHex("024200038C121910494E532E79A410D1050228001275060280F5"), encoded);
    }

    @Test
    public void testLowReservoirAlert() throws DecoderException {
        List<AlertConfiguration> configurations = new ArrayList<>();
        configurations.add(new AlertConfiguration(AlertSlot.LOW_RESERVOIR, true, (short) 0, false, AlertTriggerType.RESERVOIR_VOLUME_TRIGGER, (short) 200, BeepType.FOUR_TIMES_BIP_BEEP, BeepRepetitionType.XXX));

        byte[] encoded = new ProgramAlertsCommand(37879811, (short) 8, false, configurations).getEncoded();

        assertArrayEquals(Hex.decodeHex("02420003200C190A494E532E4C0000C801020149"), encoded);
    }

    @Test
    public void testUserExpirationAlert() throws DecoderException {
        List<AlertConfiguration> configurations = new ArrayList<>();
        configurations.add(new AlertConfiguration(AlertSlot.USER_SET_EXPIRATION, true, (short) 0, false, AlertTriggerType.TIME_TRIGGER, (short) 4079, BeepType.FOUR_TIMES_BIP_BEEP, BeepRepetitionType.XXX2));

        byte[] encoded = new ProgramAlertsCommand(37879811, (short) 15, false, configurations).getEncoded();

        assertArrayEquals(Hex.decodeHex("024200033C0C190A494E532E38000FEF030203E2"), encoded);
    }


    @Test
    public void testLumpOfCoalAlert() throws DecoderException {
        List<AlertConfiguration> configurations = new ArrayList<>();
        configurations.add(new AlertConfiguration(AlertSlot.EXPIRATION, true, (short) 55, false, AlertTriggerType.TIME_TRIGGER, (short) 5, BeepType.FOUR_TIMES_BIP_BEEP, BeepRepetitionType.XXX5));

        byte[] encoded = new ProgramAlertsCommand(37879811, (short) 10, false, configurations).getEncoded();

        assertArrayEquals(Hex.decodeHex("02420003280C190A494E532E7837000508020356"), encoded);
    }
}

package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import app.aaps.pump.omnipod.eros.driver.definition.AlertSet;
import app.aaps.pump.omnipod.eros.driver.definition.AlertSlot;
import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType;

class AcknowledgeAlertsCommandTest {

    @Test
    void testEncodingMultipleAlerts() {

        AlertSet alerts = new AlertSet(Arrays.asList(AlertSlot.SLOT0, AlertSlot.SLOT5));
        AcknowledgeAlertsCommand acknowledgeAlertsCommand = new AcknowledgeAlertsCommand(0x10203040, alerts);
        byte[] rawData = acknowledgeAlertsCommand.getRawData();
        Assertions.assertArrayEquals(new byte[]{
                MessageBlockType.ACKNOWLEDGE_ALERT.getValue(),
                5, // length
                (byte) 0x10, (byte) 0x20, (byte) 0x30, (byte) 0x40, // nonce
                (byte) 0x21 // alerts (bits 5 and 0)
        }, rawData);
    }

    @Test
    void testEncodingSingleAlert() {
        AcknowledgeAlertsCommand acknowledgeAlertsCommand = new AcknowledgeAlertsCommand(0x10203040, AlertSlot.SLOT5);
        byte[] rawData = acknowledgeAlertsCommand.getRawData();
        Assertions.assertArrayEquals(new byte[]{
                MessageBlockType.ACKNOWLEDGE_ALERT.getValue(),
                5, // length
                (byte) 0x10, (byte) 0x20, (byte) 0x30, (byte) 0x40, // nonce
                (byte) 0x20 // alerts (bit 5)
        }, rawData);
    }

    // TODO add tests
}

package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType;

class DeactivatePodCommandTest {

    @Test
    void testEncoding() {
        DeactivatePodCommand deactivatePodCommand = new DeactivatePodCommand(0x10203040);
        byte[] rawData = deactivatePodCommand.getRawData();
        Assertions.assertArrayEquals(new byte[]{
                MessageBlockType.DEACTIVATE_POD.getValue(),
                4, // length
                (byte) 0x10, (byte) 0x20, (byte) 0x30, (byte) 0x40 // nonce
        }, rawData);
    }

    // TODO add tests
}

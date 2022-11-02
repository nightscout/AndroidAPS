package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command;

import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.MessageBlockType;

import static org.junit.Assert.assertArrayEquals;

public class DeactivatePodCommandTest {

    @Test
    public void testEncoding() {
        DeactivatePodCommand deactivatePodCommand = new DeactivatePodCommand(0x10203040);
        byte[] rawData = deactivatePodCommand.getRawData();
        assertArrayEquals(new byte[]{
                MessageBlockType.DEACTIVATE_POD.getValue(),
                4, // length
                (byte) 0x10, (byte) 0x20, (byte) 0x30, (byte) 0x40 // nonce
        }, rawData);
    }

    // TODO add tests
}

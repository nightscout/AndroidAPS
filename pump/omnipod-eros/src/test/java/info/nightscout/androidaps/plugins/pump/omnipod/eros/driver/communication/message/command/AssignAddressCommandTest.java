package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command;

import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.Test;

public class AssignAddressCommandTest {

    @Test
    public void testEncoding() {
        AssignAddressCommand assignAddressCommand = new AssignAddressCommand(0x11223344);
        byte[] rawData = assignAddressCommand.getRawData();
        assertEquals(0x11, rawData[2]);
        assertEquals(0x22, rawData[3]);
        assertEquals(0x33, rawData[4]);
        assertEquals(0x44, rawData[5]);
    }

    // TODO add tests

}

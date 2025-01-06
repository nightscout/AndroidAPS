package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AssignAddressCommandTest {

    @Test
    void testEncoding() {
        AssignAddressCommand assignAddressCommand = new AssignAddressCommand(0x11223344);
        byte[] rawData = assignAddressCommand.getRawData();
        Assertions.assertEquals(0x11, rawData[2]);
        Assertions.assertEquals(0x22, rawData[3]);
        Assertions.assertEquals(0x33, rawData[4]);
        Assertions.assertEquals(0x44, rawData[5]);
    }
}

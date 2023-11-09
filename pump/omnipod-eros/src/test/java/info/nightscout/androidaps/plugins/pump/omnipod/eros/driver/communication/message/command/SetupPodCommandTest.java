package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command;

import org.joda.time.DateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import info.nightscout.pump.common.utils.ByteUtil;

class SetupPodCommandTest {
    @Test
    void testEncoding() {
        SetupPodCommand setupPodCommand = new SetupPodCommand( //
                0x1f00ee87, //
                new DateTime(2013, 4, 5, 22, 52, 0), //
                41847,  //
                240439);

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.fromHexString("03131f00ee87140404050d16340000a3770003ab37"), // From https://github.com/openaps/openomni/wiki/Command-03-Setup-Pod
                setupPodCommand.getRawData());
    }

    // TODO add tests
}

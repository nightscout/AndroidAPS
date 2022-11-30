package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command;

import static org.junit.Assert.assertArrayEquals;

import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import info.nightscout.pump.core.utils.ByteUtil;

public class SetupPodCommandTest {
    @Test
    public void testEncoding() {
        SetupPodCommand setupPodCommand = new SetupPodCommand( //
                0x1f00ee87, //
                new DateTime(2013, 4, 5, 22, 52, 0), //
                41847,  //
                240439);

        assertArrayEquals( //
                ByteUtil.fromHexString("03131f00ee87140404050d16340000a3770003ab37"), // From https://github.com/openaps/openomni/wiki/Command-03-Setup-Pod
                setupPodCommand.getRawData());
    }

    // TODO add tests
}

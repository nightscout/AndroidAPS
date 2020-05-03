package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command;

import org.joda.time.DateTime;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;

import static org.junit.Assert.assertArrayEquals;

public class ConfigurePodCommandTest {
    @Test
    public void testEncoding() {
        ConfigurePodCommand configurePodCommand = new ConfigurePodCommand( //
                0x1f00ee87, //
                new DateTime(2013, 4, 5, 22, 52, 0), //
                41847,  //
                240439);

        assertArrayEquals( //
                ByteUtil.fromHexString("03131f00ee87140404050d16340000a3770003ab37"), // From https://github.com/openaps/openomni/wiki/Command-03-Setup-Pod
                configurePodCommand.getRawData());
    }

    // TODO add tests
}

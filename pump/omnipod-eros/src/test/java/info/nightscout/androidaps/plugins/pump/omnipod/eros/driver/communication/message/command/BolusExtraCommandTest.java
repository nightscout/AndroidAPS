package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command;

import static org.junit.Assert.assertArrayEquals;

import org.joda.time.Duration;
import org.junit.jupiter.api.Test;

import info.nightscout.pump.core.utils.ByteUtil;

public class BolusExtraCommandTest {
    @Test
    public void testBolusExtraCommand() {
        BolusExtraCommand bolusExtraCommand = new BolusExtraCommand(1.25, 0.0,
                Duration.ZERO, false, true, Duration.standardHours(1),
                Duration.standardSeconds(2));

        assertArrayEquals( //
                ByteUtil.createByteArrayFromHexString("170d7c00fa00030d40000000000000"), // From https://github.com/openaps/openomni/wiki/Bolus
                bolusExtraCommand.getRawData());
    }

    @Test
    public void testTypicalPrime() {
        BolusExtraCommand bolusExtraCommand = new BolusExtraCommand(2.6, Duration.standardSeconds(1), false, false);
        assertArrayEquals(ByteUtil.fromHexString("170d000208000186a0000000000000"), //
                bolusExtraCommand.getRawData());
    }

    @Test
    public void testBolusExtraCommandWithExtraOddPulseCount() {
        BolusExtraCommand bolusExtraCommand = new BolusExtraCommand(1.25, 0D, Duration.ZERO, //
                false, true, Duration.standardHours(1), Duration.standardSeconds(2));
        assertArrayEquals(ByteUtil.fromHexString("170d7c00fa00030d40000000000000"), //
                bolusExtraCommand.getRawData());
    }

    @Test
    public void testBolusExtraCommandWithExtraOddPulseCount2() {
        BolusExtraCommand bolusExtraCommand = new BolusExtraCommand(2.05, 0D, Duration.ZERO, //
                false, false, Duration.standardHours(1), Duration.standardSeconds(2));
        assertArrayEquals(ByteUtil.fromHexString("170d3c019a00030d40000000000000"), //
                bolusExtraCommand.getRawData());
    }

    @Test
    public void testLargeBolus() {
        BolusExtraCommand bolusExtraCommand = new BolusExtraCommand(30D, 0, Duration.ZERO, //
                false, true, Duration.standardHours(1), Duration.standardSeconds(2));
        assertArrayEquals(ByteUtil.fromHexString("170d7c177000030d40000000000000"), //
                bolusExtraCommand.getRawData());
    }

    @Test
    public void testLargeBolus2() {
        BolusExtraCommand bolusExtraCommand = new BolusExtraCommand(29.95, 0, Duration.ZERO, //
                false, true, Duration.standardHours(1), Duration.standardSeconds(2));
        assertArrayEquals(ByteUtil.fromHexString("170d7c176600030d40000000000000"), //
                bolusExtraCommand.getRawData());
    }

    // TODO add square wave bolus tests
}

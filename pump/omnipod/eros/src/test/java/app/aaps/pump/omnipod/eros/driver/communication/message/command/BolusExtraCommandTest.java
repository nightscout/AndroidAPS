package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import org.joda.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.aaps.core.utils.pump.ByteUtil;

class BolusExtraCommandTest {
    @Test
    void testBolusExtraCommand() {
        BolusExtraCommand bolusExtraCommand = new BolusExtraCommand(1.25, 0.0,
                Duration.ZERO, false, true, Duration.standardHours(1),
                Duration.standardSeconds(2));

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.createByteArrayFromHexString("170d7c00fa00030d40000000000000"), // From https://github.com/openaps/openomni/wiki/Bolus
                bolusExtraCommand.getRawData());
    }

    @Test
    void testTypicalPrime() {
        BolusExtraCommand bolusExtraCommand = new BolusExtraCommand(2.6, Duration.standardSeconds(1), false, false);
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("170d000208000186a0000000000000"), //
                bolusExtraCommand.getRawData());
    }

    @Test
    void testBolusExtraCommandWithExtraOddPulseCount() {
        BolusExtraCommand bolusExtraCommand = new BolusExtraCommand(1.25, 0D, Duration.ZERO, //
                false, true, Duration.standardHours(1), Duration.standardSeconds(2));
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("170d7c00fa00030d40000000000000"), //
                bolusExtraCommand.getRawData());
    }

    @Test
    void testBolusExtraCommandWithExtraOddPulseCount2() {
        BolusExtraCommand bolusExtraCommand = new BolusExtraCommand(2.05, 0D, Duration.ZERO, //
                false, false, Duration.standardHours(1), Duration.standardSeconds(2));
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("170d3c019a00030d40000000000000"), //
                bolusExtraCommand.getRawData());
    }

    @Test
    void testLargeBolus() {
        BolusExtraCommand bolusExtraCommand = new BolusExtraCommand(30D, 0, Duration.ZERO, //
                false, true, Duration.standardHours(1), Duration.standardSeconds(2));
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("170d7c177000030d40000000000000"), //
                bolusExtraCommand.getRawData());
    }

    @Test
    void testLargeBolus2() {
        BolusExtraCommand bolusExtraCommand = new BolusExtraCommand(29.95, 0, Duration.ZERO, //
                false, true, Duration.standardHours(1), Duration.standardSeconds(2));
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("170d7c176600030d40000000000000"), //
                bolusExtraCommand.getRawData());
    }

    // TODO add square wave bolus tests
}

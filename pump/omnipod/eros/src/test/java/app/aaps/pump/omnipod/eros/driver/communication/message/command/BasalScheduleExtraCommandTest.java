package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import org.joda.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.definition.schedule.BasalSchedule;
import app.aaps.pump.omnipod.eros.driver.definition.schedule.BasalScheduleEntry;
import app.aaps.pump.omnipod.eros.driver.definition.schedule.RateEntry;

class BasalScheduleExtraCommandTest {
    @Test
    void testEncodingFromRateEntries() {
        List<RateEntry> rateEntries = RateEntry.createEntries(3.0, Duration.standardHours(24));
        BasalScheduleExtraCommand basalScheduleExtraCommand = new BasalScheduleExtraCommand( //
                false, //
                true, //
                Duration.ZERO, //
                (byte) 0, //
                689, //
                20D, //
                rateEntries);

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.createByteArrayFromHexString("130e40001aea01312d003840005b8d80"), // From https://github.com/openaps/openomni/wiki/Bolus
                basalScheduleExtraCommand.getRawData());
    }

    @Test
    void testParametersCorrectFromBasalSchedule() {
        BasalSchedule basalSchedule = new BasalSchedule(Collections.singletonList(new BasalScheduleEntry(0.05, Duration.ZERO)));
        BasalScheduleExtraCommand basalScheduleExtraCommand = new BasalScheduleExtraCommand( //
                basalSchedule, //
                Duration.standardHours(8).plus(Duration.standardMinutes(15)), //
                false, //
                true, //
                Duration.standardMinutes(1));

        Assertions.assertFalse(basalScheduleExtraCommand.isAcknowledgementBeep());
        Assertions.assertTrue(basalScheduleExtraCommand.isCompletionBeep());
        Assertions.assertEquals(0, basalScheduleExtraCommand.getCurrentEntryIndex());
        Assertions.assertEquals(180D, basalScheduleExtraCommand.getDelayUntilNextTenthOfPulseInSeconds(), 0.00001);
        Assertions.assertEquals(60, basalScheduleExtraCommand.getProgramReminderInterval().getStandardSeconds());
        Assertions.assertEquals(15.8, basalScheduleExtraCommand.getRemainingPulses(), 0.01);

        List<RateEntry> rateEntries = basalScheduleExtraCommand.getRateEntries();

        Assertions.assertEquals(1, rateEntries.size());

        RateEntry rateEntry = rateEntries.get(0);

        Assertions.assertEquals(3600.0, rateEntry.getDelayBetweenPulsesInSeconds(), 0.00000001);
        Assertions.assertEquals(24, rateEntry.getTotalPulses(), 0.001);
    }

    @Test
    void testEncodingFromBasalScheduleWithThreeEntries() {
        BasalSchedule schedule = new BasalSchedule(Arrays.asList( //
                new BasalScheduleEntry(1.05, Duration.ZERO), //
                new BasalScheduleEntry(0.9, Duration.standardHours(10).plus(Duration.standardMinutes(30))), //
                new BasalScheduleEntry(1.0, Duration.standardHours(18).plus(Duration.standardMinutes(30)))));

        BasalScheduleExtraCommand basalScheduleExtraCommand = new BasalScheduleExtraCommand(schedule, Duration.standardMinutes((0x2e + 1) * 30).minus(Duration.standardSeconds(0x1be8 / 8)),
                false, true, Duration.ZERO);

        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("131a4002009600a7d8c0089d0105944905a001312d00044c0112a880"),
                basalScheduleExtraCommand.getRawData());
    }

    @Test
    void testEncodingFromBasalScheduleWithSingleEntry() {
        BasalSchedule basalSchedule = new BasalSchedule(List.of(new BasalScheduleEntry(1.05, Duration.ZERO)));
        BasalScheduleExtraCommand basalScheduleExtraCommand = new BasalScheduleExtraCommand(basalSchedule,
                Duration.standardMinutes((0x20 + 1) * 30).minus(Duration.standardSeconds(0x33c0 / 8)),
                false, true, Duration.ZERO);
        assertBasalScheduleExtraCommandWithLessPrecision("130e40000688009cf29113b001059449",
                basalScheduleExtraCommand.getRawData());
    }

    @Test
    void testSegmentMerging() {
        List<BasalScheduleEntry> entries = Arrays.asList(
                new BasalScheduleEntry(0.8, Duration.ZERO),
                new BasalScheduleEntry(0.9, Duration.standardMinutes(180)), //
                new BasalScheduleEntry(0.85, Duration.standardMinutes(300)), //
                new BasalScheduleEntry(0.85, Duration.standardMinutes(450)), //
                new BasalScheduleEntry(0.85, Duration.standardMinutes(750)), //
                new BasalScheduleEntry(0.7, Duration.standardMinutes(900)), //
                new BasalScheduleEntry(0.9, Duration.standardMinutes(1080)), //
                new BasalScheduleEntry(1.10, Duration.standardMinutes(1200)) //
        );

        BasalSchedule basalSchedule = new BasalSchedule(entries);

        BasalScheduleExtraCommand basalScheduleExtraCommand = new BasalScheduleExtraCommand(basalSchedule,
                Duration.standardMinutes((0x2a + 1) * 30).minus(Duration.standardSeconds(0x1e50 / 8)),
                false,
                true,
                Duration.ZERO);

        assertBasalScheduleExtraCommandWithLessPrecision( //
                "132c4005026200455b9c01e0015752a0016801312d0006a40143209601a401885e6d016801312d00037000f9b074", //
                basalScheduleExtraCommand.getRawData());
    }

    @Test
    void testEncodingFromBasalScheduleWithThirteenEntries() {
        List<BasalScheduleEntry> entries = Arrays.asList(
                new BasalScheduleEntry(1.30, Duration.ZERO), //
                new BasalScheduleEntry(0.05, Duration.standardMinutes(30)), //
                new BasalScheduleEntry(1.70, Duration.standardMinutes(120)), //
                new BasalScheduleEntry(0.85, Duration.standardMinutes(150)), //
                new BasalScheduleEntry(1.00, Duration.standardMinutes(180)), //
                new BasalScheduleEntry(0.65, Duration.standardMinutes(450)), //
                new BasalScheduleEntry(0.50, Duration.standardMinutes(510)), //
                new BasalScheduleEntry(0.65, Duration.standardMinutes(570)), //
                new BasalScheduleEntry(0.60, Duration.standardMinutes(630)), //
                new BasalScheduleEntry(0.65, Duration.standardMinutes(690)), //
                new BasalScheduleEntry(1.65, Duration.standardMinutes(840)), //
                new BasalScheduleEntry(0.15, Duration.standardMinutes(930)), //
                new BasalScheduleEntry(0.85, Duration.standardMinutes(990)) //
        );

        BasalSchedule basalSchedule = new BasalSchedule(entries);
        BasalScheduleExtraCommand basalScheduleExtraCommand = new BasalScheduleExtraCommand(basalSchedule,
                Duration.standardMinutes((0x27 + 1) * 30).minus(Duration.standardSeconds(0x1518 / 8)),
                false, true, Duration.ZERO);

        assertBasalScheduleExtraCommandWithLessPrecision("1356400c02c8011abc64008200d34689000f15752a0000aa00a1904b00550143209603840112a880008201a68d13006402255100008201a68d13007801c9c380014501a68d1301ef00a675a2001e07270e0004fb01432096",
                basalScheduleExtraCommand.getRawData());
    }

    @Test
    void testBasalScheduleExtraCommandRoundsToNearestSecond() {
        BasalSchedule basalSchedule = new BasalSchedule(List.of(new BasalScheduleEntry(1.00, Duration.ZERO)));

        BasalScheduleExtraCommand basalScheduleExtraCommand = new BasalScheduleExtraCommand(basalSchedule,
                Duration.standardMinutes((0x2b + 1) * 30).minus(Duration.standardSeconds(0x1b38 / 8).plus(Duration.millis(456))),
                false, true, Duration.ZERO);

        assertBasalScheduleExtraCommandWithLessPrecision("130e400001c1006acfc012c00112a880", basalScheduleExtraCommand.getRawData());
    }


    private void assertBasalScheduleExtraCommandWithLessPrecision(String expectedHexString, byte[] actual) {
        // The XXXXXXXX field is in thousands of a millisecond. Since we use floating points for
        // recreating the offset, we can have small errors in reproducing the the encoded output, which we really
        // don't care about.

        byte[] expected = ByteUtil.INSTANCE.fromHexString(expectedHexString);

        Assertions.assertEquals(extractDelayUntilNextPulseInSeconds(expected), extractDelayUntilNextPulseInSeconds(actual), 0.0001);

        // Discard the last byte of the integer so that we can compare the other bytes of the message
        expected[9] = 0;
        actual[9] = 0;

        Assertions.assertArrayEquals(expected, actual);
    }

    private double extractDelayUntilNextPulseInSeconds(byte[] message) {
        return ByteUtil.INSTANCE.toInt(message[6], (int) message[7], (int) message[8], (int) message[9], ByteUtil.BitConversion.BIG_ENDIAN) / 1_000_000.0;
    }
}

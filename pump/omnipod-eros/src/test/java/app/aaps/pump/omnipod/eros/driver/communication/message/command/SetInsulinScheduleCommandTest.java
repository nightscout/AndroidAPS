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
import app.aaps.pump.omnipod.eros.driver.definition.schedule.BolusDeliverySchedule;

class SetInsulinScheduleCommandTest {
    @Test
    void testTemporaryBasalScheduleAlternatingSegmentFlag() {
        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand(0x9746c65b, //
                0.05, Duration.standardMinutes(30));
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("1a0e9746c65b01007901384000000000"), //
                setInsulinScheduleCommand.getRawData());

        SetInsulinScheduleCommand setInsulinScheduleCommand2 = new SetInsulinScheduleCommand(0x9746c65b, //
                0.05, Duration.standardHours(8).plus(Duration.standardMinutes(30)));
        Assertions.assertArrayEquals(ByteUtil.INSTANCE.fromHexString("1a109746c65b0100911138400000f8000000"), //
                setInsulinScheduleCommand2.getRawData());

        // Test passed before introducing a maximum temp basal duration of 12 hours
//        SetInsulinScheduleCommand setInsulinScheduleCommand3 = new SetInsulinScheduleCommand(0x9746c65b, //
//                0.05, Duration.standardHours(16).plus(Duration.standardMinutes(30)));
//        assertArrayEquals(ByteUtil.INSTANCE.fromHexString("1a129746c65b0100a92138400000f800f8000000"), //
//                setInsulinScheduleCommand3.getRawData());
    }

    @Test
    void testTemporaryBasalScheduleMessage() {
        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand(0xea2d0a3b, //
                0.2, Duration.standardMinutes(30));

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.fromHexString("1a0eea2d0a3b01007d01384000020002"), // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/TempBasalTests.swift
                setInsulinScheduleCommand.getRawData());
    }

    @Test
    void testTemporaryBasalScheduleMessageWithAlternatingPulse() {
        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand(0x4e2c2717, //
                0.05, Duration.standardMinutes(150));

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.fromHexString("1a0e4e2c271701007f05384000004800"), // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/TempBasalTests.swift
                setInsulinScheduleCommand.getRawData());
    }

    @Test
    void testLargerTemporaryBasalScheduleMessage() {
        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand(0x87e8d03a, //
                2D, Duration.standardMinutes(90));

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.fromHexString("1a0e87e8d03a0100cb03384000142014"), // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/TempBasalTests.swift
                setInsulinScheduleCommand.getRawData());
    }

    @Test
    void testExtremelyLargeTemporaryBasalScheduleMessage() {
        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand(0xa958c5ad, //
                30D, Duration.standardHours(12));

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.fromHexString("1a10a958c5ad0104f5183840012cf12c712c"), // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/TempBasalTests.swift
                setInsulinScheduleCommand.getRawData());
    }

    @Test
    void testBasalScheduleSingleEntry() {
        BasalSchedule basalSchedule = new BasalSchedule(Collections.singletonList(new BasalScheduleEntry(0.05, Duration.ZERO)));

        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand( //
                0x01020304, //
                basalSchedule, //
                Duration.standardHours(8).plus(Duration.standardMinutes(15)));

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.fromHexString("1a1201020304000064101c200000f800f800f800"), // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/BasalScheduleTests.swift
                setInsulinScheduleCommand.getRawData());
    }

    @Test
    void testBasalScheduleWithTwelveEntries() {
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
                new BasalScheduleEntry(0.85, Duration.standardMinutes(960)) //
        );
        BasalSchedule basalSchedule = new BasalSchedule(entries);

        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand( //
                0xf36a23a3, //
                basalSchedule, //
                Duration.standardMinutes((0x03 + 1) * 30).minus(Duration.standardSeconds(0x0ae8 / 8)));

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.fromHexString("1a2af36a23a3000291030ae80000000d280000111809700a180610052806100600072806001128100009e808"), // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/BasalScheduleTests.swift
                setInsulinScheduleCommand.getRawData());
    }

    @Test
    void testBasalScheduleWithThirteenEntries() {
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

        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand( //
                0x851072aa, //
                basalSchedule, //
                Duration.standardMinutes((0x27 + 1) * 30).minus(Duration.standardSeconds(0x1518 / 8)));

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.fromHexString("1a2a851072aa0001dd2715180003000d280000111809700a180610052806100600072806001118101801e808"), // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/BasalScheduleTests.swift
                setInsulinScheduleCommand.getRawData());
    }

    @Test
    void testBasalScheduleRounding() {
        List<BasalScheduleEntry> entries = Arrays.asList(
                new BasalScheduleEntry(2.75, Duration.ZERO),
                new BasalScheduleEntry(20.25, Duration.standardMinutes(60)), //
                new BasalScheduleEntry(5D, Duration.standardMinutes(90)), //
                new BasalScheduleEntry(10.1, Duration.standardMinutes(120)), //
                new BasalScheduleEntry(0.05, Duration.standardMinutes(150)), //
                new BasalScheduleEntry(3.5, Duration.standardMinutes(930)) //
        );

        BasalSchedule basalSchedule = new BasalSchedule(entries);

        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand( //
                0xc2a32da8, //
                basalSchedule, //
                Duration.standardMinutes((0x28 + 1) * 30).minus(Duration.standardSeconds(0x1af0 / 8)));

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.fromHexString("1a1ec2a32da800053a281af00010181b00ca003200650001f8008800f0230023"), // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/BasalScheduleTests.swift
                setInsulinScheduleCommand.getRawData());
    }

    @Test
    void testBasalScheduleRounding2() {
        List<BasalScheduleEntry> entries = Arrays.asList(
                new BasalScheduleEntry(0.6, Duration.ZERO),
                new BasalScheduleEntry(0.65, Duration.standardMinutes(450)), //
                new BasalScheduleEntry(0.5, Duration.standardMinutes(510)), //
                new BasalScheduleEntry(0.65, Duration.standardMinutes(570)), //
                new BasalScheduleEntry(0.15, Duration.standardMinutes(930)), //
                new BasalScheduleEntry(0.8, Duration.standardMinutes(990)) //
        );

        BasalSchedule basalSchedule = new BasalSchedule(entries);

        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand( //
                0x851072aa, //
                basalSchedule, //
                Duration.standardMinutes((0x2c + 1) * 30).minus(Duration.standardSeconds(0x2190 / 8)));

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.fromHexString("1a18851072aa00021b2c21900004f00600071005b8061801e008"), // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/BasalScheduleTests.swift
                setInsulinScheduleCommand.getRawData());
    }

    @Test
    void testBasalScheduleSegmentMerging() {
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

        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand( //
                0x851072aa, //
                basalSchedule, //
                Duration.standardMinutes((0x2a + 1) * 30).minus(Duration.standardSeconds(0x1e50 / 8)));

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.fromHexString("1a1a851072aa0002422a1e50000650083009f808380850073009700b"), // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/BasalScheduleTests.swift
                setInsulinScheduleCommand.getRawData());
    }

    @Test
    void testBolus() {
        BolusDeliverySchedule bolusDeliverySchedule = new BolusDeliverySchedule(2.6, Duration.standardSeconds(1));
        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand(0xbed2e16b, bolusDeliverySchedule);

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.createByteArrayFromHexString("1a0ebed2e16b02010a0101a000340034"), // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/BolusTests.swift
                setInsulinScheduleCommand.getRawData());
    }

    @Test
    void testBolusExtraCommandWithExtraOddPulseCount() {
        BolusDeliverySchedule bolusDeliverySchedule = new BolusDeliverySchedule(2.05, Duration.standardSeconds(2));
        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand(0xcf9e81ac, bolusDeliverySchedule);

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.createByteArrayFromHexString("1a0ecf9e81ac0200e501029000290029"), // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/BolusTests.swift
                setInsulinScheduleCommand.getRawData());
    }

    @Test
    void testLargeBolus() {
        BolusDeliverySchedule bolusDeliverySchedule = new BolusDeliverySchedule(29.95, Duration.standardSeconds(2));
        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand(0x31204ba7, bolusDeliverySchedule);

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.createByteArrayFromHexString("1a0e31204ba702014801257002570257"), // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/BolusTests.swift
                setInsulinScheduleCommand.getRawData());
    }
}

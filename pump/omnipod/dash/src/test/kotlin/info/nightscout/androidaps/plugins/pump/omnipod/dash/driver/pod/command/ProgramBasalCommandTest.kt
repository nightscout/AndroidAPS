package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.dash.driver.pod.definition.ProgramReminder
import com.google.common.truth.Truth.assertThat
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Test
import java.util.Date

@Suppress("DEPRECATION")
class ProgramBasalCommandTest {

    @Test fun testProgramBasalCommand() {
        val segments = listOf(
            BasalProgram.Segment(0.toShort(), 48.toShort(), 300)
        )
        val basalProgram = BasalProgram(segments)
        val date = Date(121, 1, 17, 14, 47, 43)

        val encoded = ProgramBasalCommand.Builder()
            .setUniqueId(37879809)
            .setNonce(1229869870)
            .setSequenceNumber(10.toShort())
            .setBasalProgram(basalProgram)
            .setCurrentTime(date)
            .setProgramReminder(ProgramReminder(false, true, 0.toByte()))
            .build()
            .encoded

        assertThat(encoded).asList().containsExactlyElementsIn(Hex.decodeHex("0242000128241A12494E532E0005E81D1708000CF01EF01EF01E130E40001593004C4B403840005B8D80827C").asList()).inOrder()
    }

    @Test fun testProgramBasalCommandWithExtraAlternateSegmentPulse() {
        val segments = listOf(
            BasalProgram.Segment(0.toShort(), 48.toShort(), 5)
        )
        val basalProgram = BasalProgram(segments)
        val date = Date(121, 0, 30, 23, 21, 46)

        val encoded = ProgramBasalCommand.Builder()
            .setUniqueId(4241)
            .setNonce(1229869870)
            .setSequenceNumber(12.toShort())
            .setBasalProgram(basalProgram)
            .setCurrentTime(date)
            .setProgramReminder(ProgramReminder(atStart = false, atEnd = false, atInterval = 0.toByte()))
            .build()
            .encoded

        assertThat(encoded).asList().containsExactlyElementsIn(Hex.decodeHex("0000109130241a12494e532e0000c52e0f700000f800f800f800130e0000000707fcad8000f015752a00033b").asList()).inOrder()
    }

    @Test fun testProgramBasalCommandAllSegments() {
        val segments = mutableListOf<BasalProgram.Segment>()
        for (segment in 0..23) {
            val rate = when (segment) {
                21   ->
                    110

                22   ->
                    120

                23   ->
                    135

                else ->
                    segment * 5
            }
            segments.add(
                BasalProgram.Segment((segment * 2).toShort(), ((segment + 1) * 2).toShort(), rate)
            )
        }

        val basalProgram = BasalProgram(segments)
        val date = Date(2021, 8, 7, 11, 9, 6)

        val cmd = ProgramBasalCommand.Builder()
            .setUniqueId(5)
            .setNonce(1229869870)
            .setSequenceNumber(2.toShort())
            .setBasalProgram(basalProgram)
            .setCurrentTime(date)
            .setMultiCommandFlag(false)
            .setProgramReminder(ProgramReminder(atStart = false, atEnd = true, atInterval = 0.toByte()))
            .build()

        val encoded = cmd.encoded
        val expected =
            "0000000508C41A28494E532E00018B16273000032000300130023003300430053006300730083009200A100B100C180D1398400B005E009E22E80002EB49D200000A15752A0000140ABA9500001E07270E000028055D4A800032044AA200003C0393870000460310BCDB005002AEA540005A02625A00006402255100006E01F360E8007801C9C380008201A68D13008C01885E6D0096016E360000A0015752A000AA0143209600B401312D0000BE01211D2800C80112A88000DC00F9B07400F000E4E1C0010E00CB73558158".lowercase()

        assertThat(encoded).asList().containsExactlyElementsIn(Hex.decodeHex(expected).asList()).inOrder()
    }

    @Test fun testProgramBasalCommandHighRates() {
        val segments = listOf(
            BasalProgram.Segment(0.toShort(), 2.toShort(), 300),
            BasalProgram.Segment(2.toShort(), 4.toShort(), 290),
            BasalProgram.Segment(4.toShort(), 6.toShort(), 280),
            BasalProgram.Segment(6.toShort(), 8.toShort(), 270),
            BasalProgram.Segment(8.toShort(), 10.toShort(), 260),
            BasalProgram.Segment(10.toShort(), 14.toShort(), 250),
            BasalProgram.Segment(14.toShort(), 18.toShort(), 235),
            BasalProgram.Segment(18.toShort(), 22.toShort(), 225),
            BasalProgram.Segment(22.toShort(), 24.toShort(), 200),
            BasalProgram.Segment(24.toShort(), 30.toShort(), 185),
            BasalProgram.Segment(30.toShort(), 34.toShort(), 165),
            BasalProgram.Segment(34.toShort(), 38.toShort(), 145),
            BasalProgram.Segment(38.toShort(), 42.toShort(), 130),
            BasalProgram.Segment(42.toShort(), 44.toShort(), 115),
            BasalProgram.Segment(44.toShort(), 46.toShort(), 100),
            BasalProgram.Segment(46.toShort(), 48.toShort(), 65),
        )

        val basalProgram = BasalProgram(segments)
        val date = Date(2021, 8, 7, 11, 12, 9)

        val cmd = ProgramBasalCommand.Builder()
            .setUniqueId(5)
            .setNonce(1229869870)
            .setSequenceNumber(7.toShort())
            .setBasalProgram(basalProgram)
            .setCurrentTime(date)
            .setMultiCommandFlag(false)
            .setProgramReminder(ProgramReminder(atStart = false, atEnd = true, atInterval = 0.toByte()))
            .build()

        val encoded = cmd.encoded
        val expected =
            "000000051C981A2C494E532E00046D162178000B101E101D101C101B101A301938173816101458123810380E300D180B100A180613684008013F008954400258005B8D800244005EB5B002300062179B021C0065B9AA02080069A34403E8006DDD0003AC0074E0360384007A12000190008954400456009476C1029400A675A2024400BD6B61020800D3468900E600EED54D00C80112A880008201A68D13809b"

        assertThat(encoded).asList().containsExactlyElementsIn(Hex.decodeHex(expected).asList()).inOrder()
    }

    @Test fun testProgramBasalCommandDifferentInterval() {
        val segments = listOf(
            BasalProgram.Segment(0.toShort(), 2.toShort(), 50),
            BasalProgram.Segment(2.toShort(), 6.toShort(), 75),
            BasalProgram.Segment(6.toShort(), 12.toShort(), 0),
            BasalProgram.Segment(12.toShort(), 20.toShort(), 135),
            BasalProgram.Segment(20.toShort(), 32.toShort(), 270),
            BasalProgram.Segment(32.toShort(), 40.toShort(), 290),
            BasalProgram.Segment(40.toShort(), 46.toShort(), 95),
            BasalProgram.Segment(46.toShort(), 48.toShort(), 15),
        )

        val basalProgram = BasalProgram(segments)
        val date = Date(2021, 8, 7, 11, 13, 50)

        val cmd = ProgramBasalCommand.Builder()
            .setUniqueId(5)
            .setNonce(1229869870)
            .setSequenceNumber(10.toShort())
            .setBasalProgram(basalProgram)
            .setCurrentTime(date)
            .setMultiCommandFlag(false)
            .setProgramReminder(ProgramReminder(atStart = false, atEnd = true, atInterval = 0.toByte()))
            .build()

        val encoded = cmd.encoded
        val expected =
            "0000000528581A1C494E532E00038E161E50000E100538075000780DB01B701D58091801133840040A100032DC82006402255100012C016E36000006EB49D200043800CB73550CA80065B9AA0910005EB5B0023A01211D28001E07270E000065"

        assertThat(encoded).asList().containsExactlyElementsIn(Hex.decodeHex(expected).asList()).inOrder()
    }
}

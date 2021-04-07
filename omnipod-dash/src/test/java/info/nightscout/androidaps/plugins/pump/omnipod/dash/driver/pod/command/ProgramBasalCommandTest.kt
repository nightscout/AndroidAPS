package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ProgramReminder
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.Test
import java.util.*

class ProgramBasalCommandTest {

    @Test @Throws(DecoderException::class) fun testProgramBasalCommand() {
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

        Assert.assertArrayEquals(
            Hex.decodeHex("0242000128241A12494E532E0005E81D1708000CF01EF01EF01E130E40001593004C4B403840005B8D80827C"),
            encoded
        )
    }

    @Test @Throws(DecoderException::class) fun testProgramBasalCommandWithExtraAlternateSegmentPulse() {
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

        Assert.assertArrayEquals(
            Hex.decodeHex("0000109130241a12494e532e0000c52e0f700000f800f800f800130e0000000707fcad8000f015752a00033b"),
            encoded
        )
    }
}

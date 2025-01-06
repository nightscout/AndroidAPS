package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.definition.ProgramReminder
import com.google.common.truth.Truth.assertThat
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Test

class ProgramTempBasalCommandTest {

    @Test fun testExtraAlternateSegmentPulseTempBasal() {
        val command = ProgramTempBasalCommand.Builder()
            .setUniqueId(37879809)
            .setNonce(1229869870)
            .setSequenceNumber(15.toShort())
            .setRateInUnitsPerHour(5.05)
            .setDurationInMinutes(60.toShort())
            .setProgramReminder(ProgramReminder(atStart = false, atEnd = true, atInterval = 0.toByte()))
            .build()

        assertThat(command.encoded).asList().containsExactlyElementsIn(Hex.decodeHex("024200013C201A0E494E532E01011102384000321832160E400003F20036634403F20036634482A6").asList()).inOrder()
    }

    @Test fun testZeroTempBasal() {
        val command = ProgramTempBasalCommand.Builder()
            .setUniqueId(37879809)
            .setNonce(1229869870)
            .setSequenceNumber(7.toShort())
            .setRateInUnitsPerHour(0.0)
            .setDurationInMinutes(300.toShort())
            .setProgramReminder(ProgramReminder(atStart = true, atEnd = true, atInterval = 0.toByte()))
            .build()

        assertThat(command.encoded).asList().containsExactlyElementsIn(Hex.decodeHex("024200011C201A0E494E532E0100820A384000009000160EC000000A6B49D200000A6B49D20001E3").asList()).inOrder()
    }

    @Test fun testZeroTempBasalShort() {
        val command = ProgramTempBasalCommand.Builder()
            .setUniqueId(37879809)
            .setNonce(1229869870)
            .setSequenceNumber(7.toShort())
            .setRateInUnitsPerHour(0.0)
            .setDurationInMinutes(30.toShort())
            .setProgramReminder(ProgramReminder(atStart = true, atEnd = true, atInterval = 0.toByte()))
            .build()

        assertThat(command.encoded).asList().containsExactlyElementsIn(Hex.decodeHex("024200011C201A0E494E532E01007901384000000000160EC00000016B49D2000001EB49D200815B").asList()).inOrder()
    }

    @Test fun testZeroTempBasalVeryLong() {
        val command = ProgramTempBasalCommand.Builder()
            .setUniqueId(37879809)
            .setNonce(1229869870)
            .setSequenceNumber(7.toShort())
            .setRateInUnitsPerHour(0.0)
            .setDurationInMinutes(720.toShort())
            .setProgramReminder(ProgramReminder(atStart = true, atEnd = true, atInterval = 0.toByte()))
            .build()

        assertThat(command.encoded).asList().containsExactlyElementsIn(Hex.decodeHex("024200011C221A10494E532E0100901838400000F0007000160EC00000186B49D20000186B49D2000132").asList()).inOrder()
    }
}

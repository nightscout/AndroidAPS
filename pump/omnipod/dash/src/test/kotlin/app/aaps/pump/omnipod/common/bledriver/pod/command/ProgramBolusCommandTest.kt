package app.aaps.pump.omnipod.common.bledriver.pod.command

import app.aaps.pump.omnipod.common.bledriver.pod.definition.ProgramReminder
import com.google.common.truth.Truth.assertThat
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProgramBolusCommandTest {

    @Test fun testProgramBolusCommand() {
        val encoded = ProgramBolusCommand.Builder()
            .setNumberOfUnits(5.0)
            .setProgramReminder(ProgramReminder(false, true, 0.toByte()))
            .setDelayBetweenPulsesInEighthSeconds(16.toByte())
            .setUniqueId(37879809)
            .setSequenceNumber(14.toShort())
            .setNonce(1229869870)
            .build()
            .encoded

        assertThat(encoded).asList().containsExactlyElementsIn(Hex.decodeHex("02420001381F1A0E494E532E02010F01064000640064170D4003E800030D4000000000000080F6").asList()).inOrder()
    }

    // 16.15 * 1000 is 16149.99...: a cut made it 16149, which is not a step of 0.05, so this bolus was refused.
    @Test fun everyStepOf005UpTo30UIsTaken() {
        for (pulses in 1..600) {
            val command = bolus(pulses * 0.05)
            assertThat(command.toString()).contains("numberOfTenthPulses=${pulses * 10},")
        }
        assertThat(bolus(16.15).toString()).contains("numberOfTenthPulses=3230,")
    }

    @Test fun anAmountOffTheStepIsStillRefused() {
        assertThrows<IllegalArgumentException> { bolus(0.07) }
    }

    private fun bolus(units: Double): ProgramBolusCommand = ProgramBolusCommand.Builder()
        .setNumberOfUnits(units)
        .setProgramReminder(ProgramReminder(false, true, 0.toByte()))
        .setDelayBetweenPulsesInEighthSeconds(16.toByte())
        .setUniqueId(37879809)
        .setSequenceNumber(14.toShort())
        .setNonce(1229869870)
        .build()
}

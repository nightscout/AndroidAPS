package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ProgramReminder
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.jupiter.api.Test

class ProgramBolusCommandTest {

    @Test @Throws(DecoderException::class) fun testProgramBolusCommand() {
        val encoded = ProgramBolusCommand.Builder()
            .setNumberOfUnits(5.0)
            .setProgramReminder(ProgramReminder(false, true, 0.toByte()))
            .setDelayBetweenPulsesInEighthSeconds(16.toByte())
            .setUniqueId(37879809)
            .setSequenceNumber(14.toShort())
            .setNonce(1229869870)
            .build()
            .encoded

        Assert.assertArrayEquals(
            Hex.decodeHex("02420001381F1A0E494E532E02010F01064000640064170D4003E800030D4000000000000080F6"),
            encoded
        )
    }
}

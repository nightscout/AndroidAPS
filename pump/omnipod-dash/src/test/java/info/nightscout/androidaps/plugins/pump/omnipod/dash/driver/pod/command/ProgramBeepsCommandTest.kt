package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BeepType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ProgramReminder
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ProgramBeepsCommandTest {

    @Test @Throws(DecoderException::class) fun testPlayTestBeep() {
        val encoded = ProgramBeepsCommand.Builder()
            .setUniqueId(37879810)
            .setSequenceNumber(11.toShort())
            .setImmediateBeepType(BeepType.FOUR_TIMES_BIP_BEEP)
            .setBasalReminder(ProgramReminder(false, false, 0.toByte()))
            .setTempBasalReminder(ProgramReminder(false, false, 0.toByte()))
            .setBolusReminder(ProgramReminder(false, false, 0.toByte()))
            .build()
            .encoded

        Assertions.assertArrayEquals(Hex.decodeHex("024200022C061E0402000000800F"), encoded)
    }
}

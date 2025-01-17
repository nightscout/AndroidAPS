package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.definition.BeepType
import app.aaps.pump.omnipod.dash.driver.pod.definition.ProgramReminder
import com.google.common.truth.Truth.assertThat
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Test

class ProgramBeepsCommandTest {

    @Test fun testPlayTestBeep() {
        val encoded = ProgramBeepsCommand.Builder()
            .setUniqueId(37879810)
            .setSequenceNumber(11.toShort())
            .setImmediateBeepType(BeepType.FOUR_TIMES_BIP_BEEP)
            .setBasalReminder(ProgramReminder(false, false, 0.toByte()))
            .setTempBasalReminder(ProgramReminder(false, false, 0.toByte()))
            .setBolusReminder(ProgramReminder(false, false, 0.toByte()))
            .build()
            .encoded

        assertThat(encoded).asList().containsExactlyElementsIn(Hex.decodeHex("024200022C061E0402000000800F").asList()).inOrder()
    }
}

package app.aaps.pump.omnipod.common.util

import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OmnipodTimeChangeTest {

    @Test
    fun `both DST directions need correction`() {
        assertThat(OmnipodTimeChange.needsCorrection(3600000, 7200000, false)).isTrue()
        assertThat(OmnipodTimeChange.needsCorrection(7200000, 3600000, false)).isTrue()
        assertThat(OmnipodTimeChange.needsCorrection(3600000, 3600000, false)).isFalse()
    }

    @Test
    fun `an unfinished correction needs recovery even when offsets match`() {
        assertThat(OmnipodTimeChange.needsCorrection(3600000, 3600000, true)).isTrue()
    }

    @Test
    fun `unknown offset is not guessed`() {
        assertThat(OmnipodTimeChange.needsCorrection(null, 3600000, false)).isFalse()
    }

    @Test
    fun `automatic correction waits for bolus and unconfirmed commands`() {
        assertThat(OmnipodTimeChange.canProgram(true, false, true, false, false)).isFalse()
        assertThat(OmnipodTimeChange.canProgram(true, false, false, true, false)).isFalse()
        assertThat(OmnipodTimeChange.canProgram(true, false, false, false, false)).isTrue()
    }

    @Test
    fun `only our own interrupted correction may resume a suspended pod`() {
        assertThat(OmnipodTimeChange.canProgram(true, true, false, false, false)).isFalse()
        assertThat(OmnipodTimeChange.canProgram(true, true, false, false, true)).isTrue()
        assertThat(OmnipodTimeChange.canProgram(false, false, false, false, true)).isFalse()
    }

    @Test
    fun `attempts are single and retries have a bounded delay`() {
        val handler = OmnipodTimeChange()
        assertThat(handler.tryStart(0)).isTrue()
        assertThat(handler.tryStart(60000)).isFalse()
        handler.finish(60000)
        assertThat(handler.tryStart(119999)).isFalse()
        assertThat(handler.tryStart(120000)).isTrue()
    }

    @Test
    fun `only the first of a run of failures sounds an alarm`() {
        val handler = OmnipodTimeChange()
        assertThat(handler.shouldSoundOnFailure()).isTrue()
        assertThat(handler.shouldSoundOnFailure()).isFalse()
        assertThat(handler.shouldSoundOnFailure()).isFalse()
        handler.resetFailureAlert()
        assertThat(handler.shouldSoundOnFailure()).isTrue()
    }

    @Test
    fun `rate follows the fixed pod offset across both DST transitions`() {
        val program = BasalProgram(listOf(
            BasalProgram.Segment(0, 4, 100),
            BasalProgram.Segment(4, 6, 200),
            BasalProgram.Segment(6, 48, 300)
        ))
        val epochAtOneUtc = 3600000L
        assertThat(program.rateAt(epochAtOneUtc, 3600000)).isEqualTo(2.0)
        assertThat(program.rateAt(epochAtOneUtc, 7200000)).isEqualTo(3.0)
        assertThat(program.rateAt(epochAtOneUtc, -3600000)).isEqualTo(1.0)
    }
}

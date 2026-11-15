package app.aaps.pump.equil.manager.command

import app.aaps.pump.equil.manager.EquilManager
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mock

/** #2454: what the pump reports it runs is read back after a set. */
class CmdBasalGetTest : TestBaseWithProfile() {

    @Mock private lateinit var equilManager: EquilManager

    /** The answer: 6 bytes, then 48 half hours of 0.00625 U steps, low byte first. */
    private fun answer(halfHours: List<Int>): ByteArray =
        ByteArray(6) + halfHours.flatMap { listOf((it and 0xFF).toByte(), ((it shr 8) and 0xFF).toByte()) }

    @Test
    fun `an hour is the insulin of its two half hours`() {
        // 0.25 U in each half hour of the first hour (0.50 U/h), then 0.40 + 0.35 U (0.75 U/h), then 1.0 U/h.
        val halfHours = listOf(40, 40, 64, 56) + List(44) { 80 }
        val cmd = CmdBasalGet(aapsLogger, preferences, equilManager)

        cmd.decodeConfirmData(answer(halfHours))

        assertThat(cmd.cmdSuccess).isTrue()
        assertThat(cmd.hourlyRates).containsExactlyElementsIn(listOf(0.5, 0.75) + List(22) { 1.0 }).inOrder()
    }

    @Test
    fun `a short answer is not a basal`() {
        val cmd = CmdBasalGet(aapsLogger, preferences, equilManager)

        cmd.decodeConfirmData(ByteArray(8))

        assertThat(cmd.cmdSuccess).isFalse()
        assertThat(cmd.hourlyRates).isNull()
    }

    /** An ack after the answer repeats nothing useful: it must not replace the table read before. */
    @Test
    fun `a later short answer keeps the table`() {
        val cmd = CmdBasalGet(aapsLogger, preferences, equilManager)

        cmd.decodeConfirmData(answer(List(48) { 80 }))
        cmd.decodeConfirmData(ByteArray(24))

        assertThat(cmd.cmdSuccess).isTrue()
        assertThat(cmd.hourlyRates).containsExactlyElementsIn(List(24) { 1.0 })
    }

    /** The rate CmdBasalSet sends is cut down to a 0.0125 U/h step, never rounded up. */
    @Test
    fun `the rate sent for a basal is cut down to the pump step`() {
        assertThat(CmdBasalSet.deliveredRate(1.0)).isEqualTo(1.0)
        assertThat(CmdBasalSet.deliveredRate(0.79)).isEqualTo(0.7875)
        assertThat(CmdBasalSet.deliveredRate(0.05)).isEqualTo(0.05)
    }
}

package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Verifies [InfusionInfoCommand] decodes the 0x91 frame: running time, reservoir with the ×100
 * hundreds byte, basal/bolus totals, raw pumpState/mode.
 */
internal class InfusionInfoCommandTest {

    /** A 20-byte 0x91 frame from the given field bytes. */
    private fun frame(
        subId: Int = 0,
        runningHour: Int = 1, runningMin: Int = 30,
        insHundreds: Int = 2, insInt: Int = 5, insDec: Int = 50,
        basalInt: Int = 3, basalDec: Int = 25,
        bolusInt: Int = 1, bolusDec: Int = 75,
        pumpState: Int = 1, mode: Int = 1,
        setHour: Int = 0, setMin: Int = 0,
        curInt: Int = 0, curDec: Int = 0,
        realHour: Int = 0, realMin: Int = 0, realSec: Int = 10
    ): ByteArray = intArrayOf(
        0x91, subId, runningHour, runningMin, insHundreds, insInt, insDec,
        basalInt, basalDec, bolusInt, bolusDec, pumpState, mode,
        setHour, setMin, curInt, curDec, realHour, realMin, realSec
    ).map { it.toByte() }.toByteArray()

    @Test
    fun `encode is 0x31 plus inquiryType 0`() {
        assertThat(InfusionInfoCommand().encode().toList()).containsExactly(0x31.toByte(), 0x00.toByte()).inOrder()
    }

    @Test
    fun `decode maps running time reservoir totals and raw state`() {
        val r = InfusionInfoCommand().decode(frame())

        assertThat(r.runningMinutes).isEqualTo(90) // 1*60 + 30
        assertThat(r.insulinRemaining).isEqualTo(205.5) // 2*100 + 5 + 50/100
        assertThat(r.infusedTotalBasalAmount).isEqualTo(3.25)
        assertThat(r.infusedTotalBolusAmount).isEqualTo(1.75)
        assertThat(r.pumpStateRaw).isEqualTo(1)
        assertThat(r.modeRaw).isEqualTo(1)
        assertThat(r.realInfusedTime).isEqualTo(10) // (0*60 + 0)*60 + 10
    }

    @Test
    fun `decode reads high byte values as unsigned`() {
        // 200 U reservoir (2*100), mode byte 0xFF must decode to 255 (raw), not -1.
        val r = InfusionInfoCommand().decode(frame(insHundreds = 2, insInt = 0, insDec = 0, mode = 0xFF, pumpState = 0x80))
        assertThat(r.insulinRemaining).isEqualTo(200.0)
        assertThat(r.modeRaw).isEqualTo(255)
        assertThat(r.pumpStateRaw).isEqualTo(128)
    }

    @Test
    fun `decode throws on wrong opcode`() {
        val bad = frame().also { it[0] = 0x92.toByte() }
        assertFailsWith<IllegalArgumentException> { InfusionInfoCommand().decode(bad) }
    }

    @Test
    fun `decode throws when frame is shorter than 20 bytes`() {
        assertFailsWith<IllegalArgumentException> {
            InfusionInfoCommand().decode(frame().copyOf(19))
        }
    }

    @Test
    fun `constructor rejects out-of-range inquiryType`() {
        assertFailsWith<IllegalArgumentException> { InfusionInfoCommand(inquiryType = 2) }
    }
}

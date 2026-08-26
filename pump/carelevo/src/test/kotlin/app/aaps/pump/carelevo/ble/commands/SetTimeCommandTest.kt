package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class SetTimeCommandTest {

    private val fixed = DateTime(2026, 7, 15, 8, 30, 45) // yy=26, MM=7, dd=15, HH=8, mm=30, ss=45

    @Test
    fun `encode packs opcode subId datetime volume aidMode`() {
        val bytes = SetTimeCommand(subId = 1, volume = 200, aidMode = 0, dateTime = fixed).encode().toList()
        assertThat(bytes).containsExactly(
            0x11.toByte(), 1.toByte(),
            26.toByte(), 7.toByte(), 15.toByte(), 8.toByte(), 30.toByte(), 45.toByte(),
            2.toByte(), 0.toByte(), // volume 200 -> [2, 0]
            0.toByte()
        ).inOrder()
    }

    @Test
    fun `volume splits into hundreds and remainder`() {
        val bytes = SetTimeCommand(subId = 0, volume = 250, aidMode = 0, dateTime = fixed).encode()
        assertThat(bytes[8]).isEqualTo(2.toByte()) // 250 / 100
        assertThat(bytes[9]).isEqualTo(50.toByte()) // 250 % 100
    }

    @Test
    fun `volume out of range throws`() {
        assertFailsWith<IllegalArgumentException> { SetTimeCommand(0, 400, 0, fixed).encode() }
    }

    @Test
    fun `normal shape decodes 0x71 result`() {
        assertThat(SetTimeCommand(1, 200, 0, fixed).decode(byteArrayOf(0x71, 0x00)).resultCode).isEqualTo(0)
    }

    @Test
    fun `normal shape rejects wrong opcode`() {
        assertFailsWith<IllegalArgumentException> { SetTimeCommand(1, 200, 0, fixed).decode(byteArrayOf(0x72, 0x00)) }
    }

    @Test
    fun `activation shape expects both info report opcodes and shares the request encoding`() {
        val cmd = SetTimeForPatchInfoCommand(subId = 0, volume = 200, aidMode = 0, dateTime = fixed)
        assertThat(cmd.expectedResponseOpcodes).containsExactly(0x93.toByte(), 0x94.toByte())
        assertThat(cmd.encode().toList())
            .isEqualTo(SetTimeCommand(0, 200, 0, fixed).encode().toList()) // identical 0x11 request
    }

    @Test
    fun `activation shape decodes the two info frames into a PatchInfoResponse`() {
        val rpt1 = byteArrayOf(0x93.toByte(), 0x00) + "EO12507099001".map { it.code.toByte() }.toByteArray()
        val rpt2 = byteArrayOf(0x94.toByte(), 0x00) +
            "T166".map { it.code.toByte() }.toByteArray() +
            byteArrayOf(0, 0, 0, 0, 0) +
            byteArrayOf(1, 2, 3, 4, 5)
        val resp = SetTimeForPatchInfoCommand(0, 200, 0, fixed).decode(
            mapOf(0x93.toByte() to rpt1, 0x94.toByte() to rpt2)
        )
        assertThat(resp.serialNumber).isEqualTo("EO12507099001")
        assertThat(resp.firmwareVersion).isEqualTo("T166")
    }
}

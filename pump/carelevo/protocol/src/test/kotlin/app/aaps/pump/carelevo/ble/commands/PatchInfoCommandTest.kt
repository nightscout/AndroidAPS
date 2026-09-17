package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Verifies [PatchInfoCommand] decodes RPT1 (0x93) + RPT2 (0x94): serial from bytes 2..14 (ASCII),
 * firmware from 2..5 (ASCII), model from 11..16 (decimal, byte 10 skipped).
 */
internal class PatchInfoCommandTest {

    private val rpt1: Byte = 0x93.toByte()
    private val rpt2: Byte = 0x94.toByte()

    private fun rpt1Frame(serial: String, result: Int = 0): ByteArray =
        byteArrayOf(rpt1, result.toByte()) + serial.map { it.code.toByte() }.toByteArray()

    /** RPT2: [0x94, result, firmware(2..5), filler(6..10), model(11..16)]. */
    private fun rpt2Frame(firmware: String, model: List<Int>, result: Int = 0): ByteArray =
        byteArrayOf(rpt2, result.toByte()) +
            firmware.map { it.code.toByte() }.toByteArray() +
            byteArrayOf(0, 0, 0, 0, 0) + // bytes 6..10 (byte 10 skipped by decode)
            model.map { it.toByte() }.toByteArray()

    @Test
    fun `encode is a bare 0x33 request`() {
        assertThat(PatchInfoCommand().encode().toList()).containsExactly(0x33.toByte())
    }

    @Test
    fun `expected response opcodes are RPT1 and RPT2`() {
        assertThat(PatchInfoCommand().expectedResponseOpcodes).containsExactly(rpt1, rpt2)
    }

    @Test
    fun `decode extracts serial firmware model and result codes`() {
        val resp = PatchInfoCommand().decode(
            mapOf(
                rpt1 to rpt1Frame("EO12507099001"),
                rpt2 to rpt2Frame("T166", listOf(1, 2, 3, 4, 5, 6))
            )
        )

        assertThat(resp.serialNumber).isEqualTo("EO12507099001")
        assertThat(resp.firmwareVersion).isEqualTo("T166")
        assertThat(resp.modelName).isEqualTo("123456") // decimal join of bytes 11..16
        assertThat(resp.serialResultCode).isEqualTo(0)
        assertThat(resp.detailResultCode).isEqualTo(0)
    }

    @Test
    fun `decode handles the real 16-byte RPT2 frame - model clamps to bytes 11 to 15`() {
        // Real pump 0x94 frame is 16 bytes (byte 16 absent); model reads bytes 11..15 only.
        val resp = PatchInfoCommand().decode(
            mapOf(
                rpt1 to rpt1Frame("EO12507099001"),
                rpt2 to rpt2Frame("T166", listOf(1, 2, 3, 4, 5)) // 5 model bytes → 16-byte frame
            )
        )
        assertThat(resp.firmwareVersion).isEqualTo("T166")
        assertThat(resp.modelName).isEqualTo("12345")
    }

    @Test
    fun `decode reads model bytes as decimal values not ascii`() {
        // Byte value 65 (ASCII 'A') must decode to "65" — model bytes are decimal, not ASCII.
        val resp = PatchInfoCommand().decode(
            mapOf(
                rpt1 to rpt1Frame("EO12507099001"),
                rpt2 to rpt2Frame("T166", listOf(65, 66, 0, 0, 0, 0))
            )
        )
        assertThat(resp.modelName).isEqualTo("65660000")
    }

    @Test
    fun `decode surfaces non-zero result codes`() {
        val resp = PatchInfoCommand().decode(
            mapOf(
                rpt1 to rpt1Frame("EO12507099001", result = 1),
                rpt2 to rpt2Frame("T166", listOf(1, 2, 3, 4, 5, 6), result = 1)
            )
        )
        assertThat(resp.serialResultCode).isEqualTo(1)
        assertThat(resp.detailResultCode).isEqualTo(1)
    }

    @Test
    fun `decode throws when RPT1 is missing`() {
        assertFailsWith<IllegalArgumentException> {
            PatchInfoCommand().decode(mapOf(rpt2 to rpt2Frame("T166", listOf(1, 2, 3, 4, 5, 6))))
        }
    }

    @Test
    fun `decode throws when RPT2 is missing`() {
        assertFailsWith<IllegalArgumentException> {
            PatchInfoCommand().decode(mapOf(rpt1 to rpt1Frame("EO12507099001")))
        }
    }

    @Test
    fun `decode throws when RPT1 is too short`() {
        assertFailsWith<IllegalArgumentException> {
            PatchInfoCommand().decode(
                mapOf(
                    rpt1 to byteArrayOf(rpt1, 0x00, 0x41), // only 3 bytes
                    rpt2 to rpt2Frame("T166", listOf(1, 2, 3, 4, 5, 6))
                )
            )
        }
    }

    @Test
    fun `decode throws when RPT2 is too short`() {
        assertFailsWith<IllegalArgumentException> {
            PatchInfoCommand().decode(
                mapOf(
                    rpt1 to rpt1Frame("EO12507099001"),
                    rpt2 to byteArrayOf(rpt2, 0x00, 0x54) // only 3 bytes
                )
            )
        }
    }
}

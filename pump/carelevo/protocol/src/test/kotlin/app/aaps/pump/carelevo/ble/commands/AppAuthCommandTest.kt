package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class AppAuthCommandTest {

    @Test
    fun `encode is opcode 0x4B then raw key byte`() {
        assertThat(AppAuthCommand(key = 0x2A).encode().toList())
            .containsExactly(0x4B.toByte(), 0x2A.toByte()).inOrder()
    }

    @Test
    fun `encode carries high key value as raw byte`() {
        assertThat(AppAuthCommand(key = 255).encode().toList())
            .containsExactly(0x4B.toByte(), 0xFF.toByte()).inOrder()
    }

    @Test
    fun `decode returns result code from byte 1`() {
        val r = AppAuthCommand(key = 1).decode(byteArrayOf(0xBB.toByte(), 0x00))
        assertThat(r.resultCode).isEqualTo(0)
    }

    @Test
    fun `decode reads non-zero result unsigned`() {
        val r = AppAuthCommand(key = 1).decode(byteArrayOf(0xBB.toByte(), 0x80.toByte()))
        assertThat(r.resultCode).isEqualTo(128)
    }

    @Test
    fun `key below range throws`() {
        assertFailsWith<IllegalArgumentException> { AppAuthCommand(key = -1) }
    }

    @Test
    fun `key above range throws`() {
        assertFailsWith<IllegalArgumentException> { AppAuthCommand(key = 256) }
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> {
            AppAuthCommand(key = 1).decode(byteArrayOf(0x4B, 0x00))
        }
    }

    @Test
    fun `decode too short throws`() {
        assertFailsWith<IllegalArgumentException> {
            AppAuthCommand(key = 1).decode(byteArrayOf(0xBB.toByte()))
        }
    }
}

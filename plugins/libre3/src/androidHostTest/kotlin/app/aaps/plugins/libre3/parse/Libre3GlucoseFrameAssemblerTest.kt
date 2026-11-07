package app.aaps.plugins.libre3.parse

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** A glucose message arrives in two pieces and must be joined before it is checked. */
class Libre3GlucoseFrameAssemblerTest {

    private val assembler = Libre3GlucoseFrameAssembler()

    @Test
    fun `a first piece of fifteen bytes is held back`() {
        val result = assembler.feed(ByteArray(15), isGlucoseChannel = true)

        assertThat(result).isNull()
        assertThat(assembler.isWaitingForSecondPiece).isTrue()
    }

    @Test
    fun `the two pieces come out joined, in the order they arrived`() {
        val first = ByteArray(15) { it.toByte() }
        val second = ByteArray(20) { (it + 15).toByte() }

        assembler.feed(first, isGlucoseChannel = true)
        val joined = assembler.feed(second, isGlucoseChannel = true)

        assertThat(joined).hasLength(35)
        assertThat(joined).isEqualTo(first + second)
        assertThat(assembler.isWaitingForSecondPiece).isFalse()
    }

    @Test
    fun `a message from another channel is never held back`() {
        val whole = ByteArray(15) { it.toByte() }

        val result = assembler.feed(whole, isGlucoseChannel = false)

        assertThat(result).isEqualTo(whole)
        assertThat(assembler.isWaitingForSecondPiece).isFalse()
    }

    @Test
    fun `a glucose message that already arrived whole is passed straight through`() {
        val whole = ByteArray(35) { it.toByte() }

        val result = assembler.feed(whole, isGlucoseChannel = true)

        assertThat(result).isEqualTo(whole)
    }

    @Test
    fun `a piece left over from a dropped link is thrown away`() {
        assembler.feed(ByteArray(15) { 0x11 }, isGlucoseChannel = true)

        assembler.reset()

        // Without the reset, this piece would be glued to a piece from the old session and the
        // joined message would be nonsense that still had to be checked.
        assertThat(assembler.isWaitingForSecondPiece).isFalse()
        val next = ByteArray(20) { 0x22 }
        assertThat(assembler.feed(next, isGlucoseChannel = true)).isEqualTo(next)
    }

    @Test
    fun `two messages in a row are each joined on their own`() {
        val firstMessage = assembler.feed(ByteArray(15) { 1 }, true).let {
            assembler.feed(ByteArray(20) { 2 }, true)
        }
        val secondMessage = assembler.feed(ByteArray(15) { 3 }, true).let {
            assembler.feed(ByteArray(20) { 4 }, true)
        }

        assertThat(firstMessage).hasLength(35)
        assertThat(secondMessage).hasLength(35)
        assertThat(secondMessage!![0]).isEqualTo(3.toByte())
    }
}

package app.aaps.pump.insight.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.math.BigInteger

/** Covers [Nonce]: construction, increment, ordering, and the little-endian productional-bytes round-trip. */
class NonceTest {

    @Test
    fun defaultNonceIsZero() {
        assertThat(Nonce().storageValue).isEqualTo(byteArrayOf(0))
    }

    @Test
    fun incrementAddsOneOrCount() {
        val n = Nonce()
        n.increment()
        assertThat(BigInteger(n.storageValue)).isEqualTo(BigInteger.ONE)
        n.increment(5)
        assertThat(BigInteger(n.storageValue)).isEqualTo(BigInteger.valueOf(6))
    }

    @Test
    fun isSmallerThanComparesMagnitude() {
        val small = Nonce()
        val big = Nonce().apply { increment() }
        assertThat(small.isSmallerThan(big)).isTrue()
        assertThat(big.isSmallerThan(small)).isFalse()
        assertThat(small.isSmallerThan(small)).isFalse() // equal is not smaller
    }

    @Test
    fun productionalBytesIs13BytesLittleEndian() {
        val n = Nonce(byteArrayOf(0x01, 0x02)) // magnitude 0x0102
        val pb = n.productionalBytes
        assertThat(pb.filledSize).isEqualTo(13)
        val bytes = pb.bytes
        assertThat(bytes.size).isEqualTo(13)
        assertThat(bytes[0]).isEqualTo(0x02.toByte()) // least-significant byte first
        assertThat(bytes[1]).isEqualTo(0x01.toByte())
        assertThat(bytes[12]).isEqualTo(0x00.toByte()) // zero padded
    }

    @Test
    fun fromProductionalBytesRoundTrips() {
        val original = Nonce(byteArrayOf(0x05, 0x06, 0x07))
        val restored = Nonce.fromProductionalBytes(original.productionalBytes.bytes)
        assertThat(BigInteger(restored.storageValue)).isEqualTo(BigInteger(original.storageValue))
    }
}

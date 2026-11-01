package app.aaps.plugins.libre3.session

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The invariant: one sensor, one driver instance. Two links on one Libre 3 break both pairings, so
 * the refusal here is what keeps a pre-soak from taking the sensor that feeds the loop — see
 * [Libre3MacArbiter].
 */
class Libre3MacArbiterTest {

    private val mac = "CC:22:DF:B8:F9:58"
    private val other = "D1:22:9C:4E:80:74"

    @BeforeEach
    fun clear() {
        Libre3MacArbiter.reset()
    }

    @Test
    fun `the first owner to ask gets the sensor`() {
        assertThat(Libre3MacArbiter.claim(mac, "prod#1")).isTrue()
        assertThat(Libre3MacArbiter.ownerOf(mac)).isEqualTo("prod#1")
    }

    @Test
    fun `the other slot is refused the same sensor`() {
        Libre3MacArbiter.claim(mac, "prod#1")
        assertThat(Libre3MacArbiter.claim(mac, "presoak#2")).isFalse()
        assertThat(Libre3MacArbiter.ownerOf(mac)).isEqualTo("prod#1")
    }

    @Test
    fun `case and padding do not let a second slot slip through`() {
        // The address comes from a stored identity, from an NFC answer or from a scan hit, and only
        // some of those upper-case it.
        Libre3MacArbiter.claim(mac, "prod#1")
        assertThat(Libre3MacArbiter.claim(" cc:22:df:b8:f9:58 ", "presoak#2")).isFalse()
    }

    @Test
    fun `an owner may re-claim what it already holds`() {
        // Reconnects and repairs inside one slot are normal and must not be refused.
        assertThat(Libre3MacArbiter.claim(mac, "prod#1")).isTrue()
        assertThat(Libre3MacArbiter.claim(mac, "prod#1")).isTrue()
        assertThat(Libre3MacArbiter.claim(mac.lowercase(), "prod#1")).isTrue()
    }

    @Test
    fun `the two slots may hold two different sensors`() {
        assertThat(Libre3MacArbiter.claim(mac, "prod#1")).isTrue()
        assertThat(Libre3MacArbiter.claim(other, "presoak#2")).isTrue()
    }

    @Test
    fun `moving an owner to another sensor frees the one it left`() {
        Libre3MacArbiter.claim(mac, "prod#1")
        assertThat(Libre3MacArbiter.claim(other, "prod#1")).isTrue()
        assertThat(Libre3MacArbiter.ownerOf(mac)).isNull()
        // The sensor production walked away from is now free for the pre-soak.
        assertThat(Libre3MacArbiter.claim(mac, "presoak#2")).isTrue()
    }

    @Test
    fun `release hands the sensor back`() {
        Libre3MacArbiter.claim(mac, "prod#1")
        Libre3MacArbiter.release("prod#1")
        assertThat(Libre3MacArbiter.ownerOf(mac)).isNull()
        assertThat(Libre3MacArbiter.claim(mac, "presoak#2")).isTrue()
    }

    @Test
    fun `releasing an owner that holds nothing is harmless`() {
        Libre3MacArbiter.claim(mac, "prod#1")
        Libre3MacArbiter.release("presoak#2")
        assertThat(Libre3MacArbiter.ownerOf(mac)).isEqualTo("prod#1")
    }

    @Test
    fun `a blank address is never claimable`() {
        assertThat(Libre3MacArbiter.claim("", "prod#1")).isFalse()
        assertThat(Libre3MacArbiter.claim("   ", "prod#1")).isFalse()
        assertThat(Libre3MacArbiter.ownerOf("")).isNull()
    }

    @Test
    fun `a promoted instance keeps its sensor when the next pre-soak starts`() {
        // The defect this guards against: the driver's slot NAME stays "presoak" after a promotion,
        // so keying the map on the name would let the second pre-soak take the promoted instance's
        // sensor away, and one sensor would be held by two slots again. The token is per instance,
        // so the promoted one keeps what it holds.
        val promoted = "presoak#1"
        val nextPresoak = "presoak#2"
        Libre3MacArbiter.claim(mac, promoted)

        assertThat(Libre3MacArbiter.claim(mac, nextPresoak)).isFalse()
        assertThat(Libre3MacArbiter.claim(other, nextPresoak)).isTrue()
        assertThat(Libre3MacArbiter.ownerOf(mac)).isEqualTo(promoted)
    }
}

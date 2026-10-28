package app.aaps.plugins.dexcomoneplus.session

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The invariant: one transmitter, one slot. Two sessions on one sensor corrupt both KEKS handshakes,
 * so a refusal here is what keeps an activation from failing — see [OnePlusMacArbiter].
 */
class OnePlusMacArbiterTest {

    private val mac = "C8:4E:07:B2:31:1F"
    private val other = "D1:22:9C:4E:80:74"

    @BeforeEach
    fun clear() {
        OnePlusMacArbiter.reset()
    }

    @Test
    fun `the first slot to ask gets the sensor`() {
        assertThat(OnePlusMacArbiter.claim(mac, "prod")).isTrue()
        assertThat(OnePlusMacArbiter.ownerOf(mac)).isEqualTo("prod")
    }

    @Test
    fun `the other slot is refused the same sensor`() {
        OnePlusMacArbiter.claim(mac, "prod")
        assertThat(OnePlusMacArbiter.claim(mac, "staging")).isFalse()
        assertThat(OnePlusMacArbiter.ownerOf(mac)).isEqualTo("prod")
    }

    @Test
    fun `case and padding do not let a second slot slip through`() {
        // A MAC reaches the stores from a scan hit, an applicator parse or a restored session, and
        // only some of those upper-case it.
        OnePlusMacArbiter.claim(mac, "prod")
        assertThat(OnePlusMacArbiter.claim(" c8:4e:07:b2:31:1f ", "staging")).isFalse()
    }

    @Test
    fun `a slot may re-claim what it already holds`() {
        // Reconnects and repairs inside one slot are normal and must not be refused.
        assertThat(OnePlusMacArbiter.claim(mac, "prod")).isTrue()
        assertThat(OnePlusMacArbiter.claim(mac, "prod")).isTrue()
        assertThat(OnePlusMacArbiter.claim(mac.lowercase(), "prod")).isTrue()
    }

    @Test
    fun `the two slots may hold two different sensors`() {
        assertThat(OnePlusMacArbiter.claim(mac, "prod")).isTrue()
        assertThat(OnePlusMacArbiter.claim(other, "staging")).isTrue()
    }

    @Test
    fun `moving a slot to another sensor frees the one it left`() {
        OnePlusMacArbiter.claim(mac, "prod")
        assertThat(OnePlusMacArbiter.claim(other, "prod")).isTrue()
        assertThat(OnePlusMacArbiter.ownerOf(mac)).isNull()
        // The sensor production walked away from is now available to the other slot.
        assertThat(OnePlusMacArbiter.claim(mac, "staging")).isTrue()
    }

    @Test
    fun `release hands the sensor back`() {
        OnePlusMacArbiter.claim(mac, "prod")
        OnePlusMacArbiter.release("prod")
        assertThat(OnePlusMacArbiter.ownerOf(mac)).isNull()
        assertThat(OnePlusMacArbiter.claim(mac, "staging")).isTrue()
    }

    @Test
    fun `releasing a slot that holds nothing is harmless`() {
        OnePlusMacArbiter.claim(mac, "prod")
        OnePlusMacArbiter.release("staging")
        assertThat(OnePlusMacArbiter.ownerOf(mac)).isEqualTo("prod")
    }

    @Test
    fun `a blank address is never claimable`() {
        assertThat(OnePlusMacArbiter.claim("", "prod")).isFalse()
        assertThat(OnePlusMacArbiter.claim("   ", "prod")).isFalse()
        assertThat(OnePlusMacArbiter.ownerOf("")).isNull()
    }
}

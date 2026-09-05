package app.aaps.core.nssdk.exceptions

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Asserts that every exception this client throws is still a `java.io.IOException` on the JVM.
 *
 * The base class moved from `java.io.IOException` to `kotlinx.io.IOException` so the module can build
 * for iOS. On the JVM kotlinx-io declares
 * `actual typealias IOException = java.io.IOException`, so nothing changes - but "so it should be
 * fine" is exactly the reasoning that let the Gson regression through, and the failure mode here is
 * ugly: a `catch (e: IOException)` that silently stops catching. The compiler would not complain,
 * and neither would any other test.
 *
 * Three call sites depend on it:
 * - `PairingOfferFetcher.fetchAndUnwrap` - a network failure while looking for pairing offers must
 *   surface as "no offer", not as a crash on the pairing screen.
 * - `PairingOfferPublisher.publishOffer` / `deleteOffer` - `deleteOffer` must never throw, because a
 *   pairing offer left behind on the server keeps a PIN brute-force window open.
 *
 * If this file ever fails, do not change the assertion - the exception hierarchy has silently
 * stopped being catchable and those three sites need looking at.
 */
class NightscoutExceptionTest {

    private val all = listOf(
        DateHeaderOutOfToleranceException("m"),
        InvalidAccessTokenException("m"),
        InvalidFormatNightscoutException("m"),
        InvalidParameterNightscoutException("m"),
        UnknownResponseNightscoutException("m"),
        UnsuccessfulNightscoutException("m")
    )

    @Test
    fun `every exception is a java IOException`() {
        for (exception in all)
            assertThat(exception).isInstanceOf(java.io.IOException::class.java)
    }

    /** The catch sites are written against `java.io.IOException`, so prove that shape works. */
    @Test
    fun `each one is caught by a java IOException catch block`() {
        for (exception in all) {
            var caught = false
            try {
                throw exception
            } catch (_: java.io.IOException) {
                caught = true
            }
            assertThat(caught).isTrue()
        }
    }

    /** They all keep a non-null message - workers write it straight into the user visible log. */
    @Test
    fun `every exception carries its message`() {
        for (exception in all)
            assertThat(exception.message).isEqualTo("m")
    }

    /** And they are all still `NightscoutException`, which is what the retry exclusion list matches on. */
    @Test
    fun `every exception is a NightscoutException`() {
        for (exception in all)
            assertThat(exception).isInstanceOf(NightscoutException::class.java)
    }
}

package app.aaps.core.data.time

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.TimeZone as JavaTimeZone

/**
 * The keeping and the re-reading of [SystemTimeZone], proved by moving the default zone under it.
 *
 * Only the JVM lets a test do that (`java.util.TimeZone.setDefault` is what
 * `TimeZone.currentSystemDefault()` reads on this platform), which is why these cases live here and
 * not in `SystemTimeZoneCacheTest`.
 *
 * Both zones used are real ones with an hour between them, so a mix-up cannot pass by accident, and
 * the original default is put back afterwards - the object is shared by the whole test run.
 */
class SystemTimeZoneCacheJvmTest {

    private lateinit var original: JavaTimeZone

    /** Any fixed moment. The object only ever subtracts two of these, so the value does not matter. */
    private val readAt = 1_000_000L

    @BeforeEach fun pinZone() {
        original = JavaTimeZone.getDefault()
        JavaTimeZone.setDefault(JavaTimeZone.getTimeZone("Europe/Prague"))
        SystemTimeZone.invalidate()
    }

    @AfterEach fun restoreZone() {
        JavaTimeZone.setDefault(original)
        SystemTimeZone.invalidate()
    }

    @Test
    fun `the zone read once is kept inside the interval`() {
        assertThat(SystemTimeZone.currentAt(readAt).id).isEqualTo("Europe/Prague")
        JavaTimeZone.setDefault(JavaTimeZone.getTimeZone("Europe/London"))

        assertThat(SystemTimeZone.currentAt(readAt).id).isEqualTo("Europe/Prague")
        assertThat(SystemTimeZone.currentAt(readAt + SystemTimeZone.RECHECK_INTERVAL_MILLIS - 1).id).isEqualTo("Europe/Prague")
    }

    @Test
    fun `invalidate shows the new zone at once`() {
        assertThat(SystemTimeZone.currentAt(readAt).id).isEqualTo("Europe/Prague")
        JavaTimeZone.setDefault(JavaTimeZone.getTimeZone("Europe/London"))

        SystemTimeZone.invalidate()

        // The same moment as the first read: only the invalidation can have changed the answer.
        assertThat(SystemTimeZone.currentAt(readAt).id).isEqualTo("Europe/London")
    }

    @Test
    fun `the zone is read again once the interval has passed`() {
        assertThat(SystemTimeZone.currentAt(readAt).id).isEqualTo("Europe/Prague")
        JavaTimeZone.setDefault(JavaTimeZone.getTimeZone("Europe/London"))

        // Nothing reported the change - this is the backstop that every platform keeps.
        assertThat(SystemTimeZone.currentAt(readAt + SystemTimeZone.RECHECK_INTERVAL_MILLIS).id).isEqualTo("Europe/London")
    }

    @Test
    fun `a clock that went backwards reads the zone again`() {
        assertThat(SystemTimeZone.currentAt(readAt).id).isEqualTo("Europe/Prague")
        JavaTimeZone.setDefault(JavaTimeZone.getTimeZone("Europe/London"))

        // The user set the time by hand, or NTP stepped it back. Holding the zone for the whole
        // interval measured from a moment in the future would mean holding it for as long as the
        // step was long.
        assertThat(SystemTimeZone.currentAt(readAt - 1).id).isEqualTo("Europe/London")
    }
}

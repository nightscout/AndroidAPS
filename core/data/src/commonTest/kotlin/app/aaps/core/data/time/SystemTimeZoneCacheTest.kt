package app.aaps.core.data.time

import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What [SystemTimeZone] has to do on every platform.
 *
 * Honest limit: a test cannot move the system zone from common code, so these cases prove that the
 * kept value is the right one and that it is still the right one after [SystemTimeZone.invalidate],
 * not that a second call avoided the read. The cases that prove the keeping and the re-reading are in
 * `SystemTimeZoneCacheJvmTest`, where the default zone can be moved under the object.
 *
 * They are still worth running on Apple: reading the zone there goes through
 * `NSTimeZone.resetSystemTimeZone()` and the zone file on disk, so this is where a platform that
 * answers differently on the second call would show up.
 */
class SystemTimeZoneCacheTest {

    @BeforeTest fun forgetBefore() = SystemTimeZone.invalidate()

    @AfterTest fun forgetAfter() = SystemTimeZone.invalidate()

    @Test
    fun `current is the system zone`() {
        assertEquals(TimeZone.currentSystemDefault(), SystemTimeZone.current())
    }

    @Test
    fun `every call inside the interval gives the same zone`() {
        val read = 1_000_000L
        val first = SystemTimeZone.currentAt(read)
        assertEquals(first, SystemTimeZone.currentAt(read))
        assertEquals(first, SystemTimeZone.currentAt(read + SystemTimeZone.RECHECK_INTERVAL_MILLIS - 1))
    }

    @Test
    fun `the zone is still right after the interval`() {
        val read = 1_000_000L
        val first = SystemTimeZone.currentAt(read)
        assertEquals(first, SystemTimeZone.currentAt(read + SystemTimeZone.RECHECK_INTERVAL_MILLIS))
        assertEquals(TimeZone.currentSystemDefault(), SystemTimeZone.currentAt(read + SystemTimeZone.RECHECK_INTERVAL_MILLIS))
    }

    @Test
    fun `the zone is still right after invalidate`() {
        SystemTimeZone.current()
        SystemTimeZone.invalidate()
        assertEquals(TimeZone.currentSystemDefault(), SystemTimeZone.current())
    }
}

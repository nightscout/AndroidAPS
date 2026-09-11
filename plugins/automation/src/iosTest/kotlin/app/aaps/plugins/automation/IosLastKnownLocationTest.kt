package app.aaps.plugins.automation

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocation
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Reading a Core Location fix, and the distance between two of them.
 *
 * `CLLocationManager` is deliberately not touched here: asking it for authorization without the
 * usage description in the running bundle terminates the process, so a test that constructed one
 * would take the whole run down. What is tested is everything around it that could be wrong.
 */
@OptIn(ExperimentalForeignApi::class)
class IosLastKnownLocationTest {

    /** Prague, and a point due east of it. */
    private val prague = CLLocation(latitude = 50.0755, longitude = 14.4378)

    /**
     * The mistake this test exists for.
     *
     * Latitude and longitude are both doubles, so swapping them in the `useContents` block compiles
     * and passes review, and then every location trigger fires in the wrong place. Prague's two
     * coordinates are far enough apart that a swap cannot look correct.
     */
    @Test
    fun `a fix keeps latitude and longitude the right way round`() {
        val position = prague.toGeoPosition()

        assertTrue(abs(position.latitude - 50.0755) < 0.0001, "latitude was ${position.latitude}")
        assertTrue(abs(position.longitude - 14.4378) < 0.0001, "longitude was ${position.longitude}")
    }

    @Test
    fun `the distance to the same point is zero`() {
        assertTrue(prague.distanceFromLocation(prague) < 1.0)
    }

    /**
     * Roughly one degree of longitude at this latitude, which is about 71 km.
     *
     * Loose bounds on purpose: the point is that the platform computes a real geodesic distance, not
     * that it matches a particular formula to the metre. Pinning it tighter would make the test a
     * statement about Apple's implementation rather than about ours.
     */
    @Test
    fun `a degree of longitude is about seventy kilometres here`() {
        val east = CLLocation(latitude = 50.0755, longitude = 15.4378)

        val metres = prague.distanceFromLocation(east)

        assertTrue(metres in 65_000.0..75_000.0, "expected about 71 km, got $metres m")
    }

    /** Distance is symmetric, so argument order at the call site cannot matter. */
    @Test
    fun `distance does not depend on which point is asked`() {
        val east = CLLocation(latitude = 50.0755, longitude = 15.4378)

        assertTrue(abs(prague.distanceFromLocation(east) - east.distanceFromLocation(prague)) < 1.0)
    }
}

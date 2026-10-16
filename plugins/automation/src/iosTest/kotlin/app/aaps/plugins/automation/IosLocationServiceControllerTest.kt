package app.aaps.plugins.automation

import kotlin.test.Test
import kotlin.test.assertNull

/**
 * That nothing reads a location until automation has asked for one.
 *
 * [IosLastKnownLocation] used to keep its own `CLLocationManager` behind a `by lazy` that called
 * `requestWhenInUseAuthorization()` and `startUpdatingLocation()` on first read. So opening the
 * automation trigger editor once - with no location trigger configured - started location updates,
 * raised a permission prompt, and lit the location indicator, and nothing in AAPS could turn any of
 * it off again. On this platform the automation runtime is deliberately never started, so
 * `TriggerLocation` cannot fire: the updates bought nothing at all.
 *
 * There is one manager now, owned here, and `AutomationRuntime.updateLocationService()` decides when
 * it runs - the same shared call Android makes. The reader is passive, like
 * `LastLocationDataContainer` on Android.
 *
 * Only the "not asked for" half can be checked without a device: it short-circuits before the lazy
 * manager is touched, so no Core Location object is constructed. Whether a real fix arrives once
 * updates are on needs the simulator.
 */
class IosLocationServiceControllerTest {

    private val sut = IosLocationServiceController(SilentLogger)

    @Test
    fun `there is no location until updates are asked for`() {
        assertNull(sut.lastLocation())
    }

    @Test
    fun `there is no location after updates are turned off again`() {
        sut.setLocationUpdatesEnabled(false)

        assertNull(sut.lastLocation())
    }
}

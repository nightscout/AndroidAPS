package app.aaps.plugins.automation

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins that iOS reports no location permission groups.
 *
 * Empty looks like an unfinished implementation and is not: the groups carry Android permission
 * strings, and iOS has nothing to put in them - it asks for location at the point of use instead.
 * `AutomationRuntime` reads this to decide what to tell the user is missing, so returning a made-up
 * group would produce a permission prompt for something the user cannot grant.
 */
class IosLocationPermissionsTest {

    @Test
    fun `iOS reports no permission groups to request`() {
        assertTrue(IosLocationPermissions().groups().isEmpty())
    }

    @Test
    fun `asking twice stays empty`() {
        val permissions = IosLocationPermissions()

        assertTrue(permissions.groups().isEmpty())
        assertTrue(permissions.groups().isEmpty())
    }
}

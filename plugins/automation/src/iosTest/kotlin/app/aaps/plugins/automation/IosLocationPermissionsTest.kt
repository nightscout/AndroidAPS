package app.aaps.plugins.automation

import app.aaps.core.interfaces.plugin.IosPermission
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * iOS reports location as one group with the iOS id, which `IosPluginPermissions` can check.
 *
 * `AutomationRuntime` decides when to list it (only while a rule uses a location trigger); this only
 * says what the group is.
 */
class IosLocationPermissionsTest {

    @Test
    fun `location is one group with the iOS id`() {
        val groups = IosLocationPermissions().groups()

        assertEquals(1, groups.size)
        assertEquals(listOf(IosPermission.LOCATION), groups.single().permissions)
    }
}

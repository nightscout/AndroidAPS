package app.aaps.plugins.automation

import app.aaps.core.interfaces.plugin.PermissionGroup
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * No permission groups, because iOS has no permission model of this shape.
 *
 * `PermissionGroup.permissions` holds platform permission strings - Android's `Manifest.permission`
 * constants. iOS has no equivalent list: location access is asked for at the point of use, by
 * `CLLocationManager.requestWhenInUseAuthorization`, and the system decides when to show the prompt.
 *
 * An empty list is the answer the interface itself calls for, and it is honest rather than silent:
 * `AutomationRuntime` then reports nothing missing, because on iOS nothing is. The permission is
 * still requested - see `IosLocationServiceController` - just not through this list.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class IosLocationPermissions @Inject constructor() : LocationPermissions {

    override fun groups(): List<PermissionGroup> = emptyList()
}

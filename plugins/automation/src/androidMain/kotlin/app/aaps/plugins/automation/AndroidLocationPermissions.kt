package app.aaps.plugins.automation

import android.Manifest
import app.aaps.core.interfaces.plugin.PermissionGroup
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Two groups, not one: Android will not grant background location in the same request as foreground,
 * so asking for them together silently gets neither.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidLocationPermissions @Inject constructor() : LocationPermissions {

    override fun groups(): List<PermissionGroup> = listOf(
        PermissionGroup(
            permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            rationaleTitle = AutomationStrings.permission_location_title,
            rationaleDescription = AutomationStrings.permission_location_description,
        ),
        PermissionGroup(
            permissions = listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            rationaleTitle = AutomationStrings.permission_location_title,
            rationaleDescription = AutomationStrings.permission_background_location_description,
        ),
    )
}

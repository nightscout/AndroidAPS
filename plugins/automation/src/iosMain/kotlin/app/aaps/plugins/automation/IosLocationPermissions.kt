package app.aaps.plugins.automation

import app.aaps.core.interfaces.plugin.IosPermission
import app.aaps.core.interfaces.plugin.PermissionGroup
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Location, as one group with the iOS id `IosPermission.LOCATION`.
 *
 * iOS asks for location itself, at the point of use (`IosLocationServiceController`), but the answer can
 * be read at any time. `AutomationRuntime` lists this group only while a rule uses a location trigger, so
 * the permission screen shows a refusal then, and the user knows why the rule never fires.
 *
 * One group, not Android's two: iOS AAPS asks only for "while using the app", so there is no separate
 * background location to ask for.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosLocationPermissions() : LocationPermissions {

    override fun groups(): List<PermissionGroup> = listOf(
        PermissionGroup(
            permissions = listOf(IosPermission.LOCATION),
            rationaleTitle = AutomationStrings.permission_location_title,
            rationaleDescription = AutomationStrings.permission_location_description
        )
    )
}

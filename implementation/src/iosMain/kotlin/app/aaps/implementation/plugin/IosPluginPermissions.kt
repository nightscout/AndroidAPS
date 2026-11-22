package app.aaps.implementation.plugin

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.IosPermission
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PermissionProvider
import app.aaps.core.interfaces.plugin.PluginPermissions
import app.aaps.implementation.ImplementationStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The permissions the app needs on iOS, as iOS reports them, so a refused one is shown.
 *
 * iOS has no list of permissions like Android's `Manifest.permission`, and it asks the user itself the
 * first time an app uses something. But the answer can be read at any time, and a refusal is silent:
 * a refused notification permission means alarms that never show, a refused Bluetooth means a pump that
 * cannot be reached. So the permission screen shows them, as on Android:
 *
 * - **Notifications**, always. Alarms reach the user through them, on the master and on a client.
 * - **Bluetooth**, from every enabled pump (`PumpPluginBase.requiredPermissions`).
 * - **Location**, from automation, only while a rule uses a location trigger.
 *
 * Groups carry the ids in [IosPermission]. A group with any other id cannot be checked on iOS, so it is
 * left out and logged as an error: listing it as missing would send the user looking for a switch iOS
 * does not have, and listing it as granted would claim a check that never happened.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosPluginPermissions(
    private val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val status: IosPermissionStatus,
    private val permissionProviders: () -> Set<PermissionProvider>
) : PluginPermissions {

    override fun collectMissingPermissions(): List<PermissionGroup> =
        collectAllPermissions().filter { group -> group.permissions.any { isMissing(it) } }

    /** Each group once, even when several pumps or plugins declare the same one. */
    override fun collectAllPermissions(): List<PermissionGroup> =
        (listOf(notifications) + declaredGroups()).distinctBy { it.permissions.toSet() }

    private fun isMissing(permission: String): Boolean = when (status.state(permission)) {
        IosPermissionState.DENIED, IosPermissionState.NOT_ASKED -> true
        IosPermissionState.GRANTED, IosPermissionState.UNKNOWN  -> false
    }

    private fun declaredGroups(): List<PermissionGroup> {
        val declared = activePlugin.getPluginsList().filter { it.isEnabled() }.flatMap { it.requiredPermissions() } +
            permissionProviders().flatMap { it.requiredPermissions() }
        val (checkable, unknown) = declared.partition { group -> group.permissions.isNotEmpty() && group.permissions.all { it in KNOWN } }
        if (unknown.isNotEmpty()) {
            aapsLogger.error(
                LTag.CORE,
                "A plugin declares permissions iOS cannot check: " + unknown.flatMap { it.permissions }.distinct().joinToString()
            )
        }
        return checkable
    }

    private companion object {

        val KNOWN = setOf(IosPermission.BLUETOOTH, IosPermission.NOTIFICATIONS, IosPermission.LOCATION)

        val notifications = PermissionGroup(
            permissions = listOf(IosPermission.NOTIFICATIONS),
            rationaleTitle = ImplementationStrings.permission_notifications_title,
            rationaleDescription = ImplementationStrings.permission_notifications_description
        )
    }
}

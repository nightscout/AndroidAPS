package app.aaps.implementation.plugin

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PermissionProvider
import app.aaps.core.interfaces.plugin.PluginPermissions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * No permission groups, because on iOS there is nothing to put in one.
 *
 * `PermissionGroup.permissions` holds platform permission strings, which are Android
 * `Manifest.permission` constants. iOS has no list to enumerate and no way to ask whether something
 * was granted ahead of time: an app requests access at the point of use and the system decides when
 * to show the prompt. So a permissions screen has nothing to show, and that is the true answer
 * rather than a missing one.
 *
 * It is also the answer every source already gives. `bluetoothPermissionGroup()` is null on iOS,
 * `IosLocationPermissions` returns an empty list, and every other place that builds a group -
 * `DexcomPlugin`, `AndroidLocationPermissions`, the global battery and notification groups - is
 * Android only.
 *
 * Rather than return a hard coded empty list, this asks the plugins anyway and complains if one ever
 * answers. That keeps the class honest: the emptiness above is a fact about today's code, not a rule
 * of the platform, and the day someone adds an iOS group this says so instead of dropping it.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosPluginPermissions @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val permissionProviders: () -> Set<PermissionProvider>
) : PluginPermissions {

    override fun collectMissingPermissions(): List<PermissionGroup> = noneAndSayIfWrong()

    override fun collectAllPermissions(): List<PermissionGroup> = noneAndSayIfWrong()

    /**
     * Always empty, but looks first.
     *
     * The check is the point. Reporting a group as missing would send the user hunting for a switch
     * iOS does not have, and reporting one as granted would claim a check that never happened -
     * so neither list may carry a group while there is no way to ask the system about it.
     */
    private fun noneAndSayIfWrong(): List<PermissionGroup> {
        val declared = activePlugin.getPluginsList().filter { it.isEnabled() }.flatMap { it.requiredPermissions() } +
            permissionProviders().flatMap { it.requiredPermissions() }
        if (declared.isNotEmpty()) {
            aapsLogger.error(
                LTag.CORE,
                "A plugin declares permissions on iOS, which nothing can grant or check: " +
                    declared.flatMap { it.permissions }.distinct().joinToString()
            )
        }
        return emptyList()
    }
}

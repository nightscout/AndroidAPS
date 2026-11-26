package app.aaps.core.interfaces.plugin

import app.aaps.core.keys.interfaces.TextRef

/**
 * Declares a group of related runtime permissions that a plugin requires.
 *
 * @param permissions Android permission strings (e.g. Manifest.permission.BLUETOOTH_CONNECT)
 * @param rationaleTitle short title for the permission group (e.g. "Bluetooth")
 * @param rationaleDescription user-facing explanation of why permissions are needed
 * @param special true for permissions that cannot be requested via the standard
 *   [android.app.Activity.requestPermissions] flow (e.g. SCHEDULE_EXACT_ALARM,
 *   REQUEST_IGNORE_BATTERY_OPTIMIZATIONS). These are skipped by [PluginBase.missingPermissions]
 *   and require a dedicated Settings intent to grant.
 * @param alwaysShowAction true if the action button should be shown even when granted
 *   (e.g. directory selection where the user may want to change the current choice)
 * @param optional true for a permission that is recommended, not needed: the app works without it, and the user may
 *   decline it ("Not now"). A declined one is still listed, but no longer opens the permissions sheet by itself
 *   (decided 2026-11-26, for "display over other apps").
 */
data class PermissionGroup(
    val permissions: List<String>,
    val rationaleTitle: TextRef,
    val rationaleDescription: TextRef,
    val special: Boolean = false,
    val alwaysShowAction: Boolean = false,
    val optional: Boolean = false,
)

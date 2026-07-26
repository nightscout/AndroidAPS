package app.aaps.core.interfaces.plugin

import androidx.annotation.StringRes

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
 */
data class PermissionGroup(
    val permissions: List<String>,
    @StringRes val rationaleTitle: Int,
    @StringRes val rationaleDescription: Int,
    val special: Boolean = false,
    val alwaysShowAction: Boolean = false,
)

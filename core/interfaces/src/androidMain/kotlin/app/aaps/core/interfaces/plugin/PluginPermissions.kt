package app.aaps.core.interfaces.plugin

import android.content.Context

/**
 * Permission state collected across all enabled plugins.
 *
 * Kept apart from [ActivePlugin] because it needs a [Context] and the Android permission model,
 * and both exist only on Android. The same store implements both interfaces.
 */
interface PluginPermissions {

    /**
     * Collects missing permissions across all enabled plugins, deduplicated by permission set.
     */
    fun collectMissingPermissions(context: Context): List<PermissionGroup>

    /**
     * Collects all required permissions (both global and plugin-declared),
     * regardless of grant status. Used by the permission UI to show both
     * granted and missing permissions.
     */
    fun collectAllPermissions(context: Context): List<PermissionGroup>
}

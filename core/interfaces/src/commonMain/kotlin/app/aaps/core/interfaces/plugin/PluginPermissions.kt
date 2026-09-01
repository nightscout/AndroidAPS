package app.aaps.core.interfaces.plugin

/**
 * Permission state collected across all enabled plugins.
 *
 * Kept apart from [ActivePlugin] because it is about the platform's permission model rather than
 * about plugins. The implementation holds whatever it needs to ask the platform - on Android a
 * `Context` - so callers do not have to supply one, and a screen listing permissions can be shared.
 */
interface PluginPermissions {

    /**
     * Collects missing permissions across all enabled plugins, deduplicated by permission set.
     */
    fun collectMissingPermissions(): List<PermissionGroup>

    /**
     * Collects all required permissions (both global and plugin-declared),
     * regardless of grant status. Used by the permission UI to show both
     * granted and missing permissions.
     */
    fun collectAllPermissions(): List<PermissionGroup>
}

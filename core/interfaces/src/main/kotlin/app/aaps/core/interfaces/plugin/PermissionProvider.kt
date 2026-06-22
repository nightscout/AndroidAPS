package app.aaps.core.interfaces.plugin

/**
 * Runtime-permission source for features that are NOT [PluginBase]s.
 *
 * Plugin permissions are collected from [PluginBase.requiredPermissions]; this is the parallel hook
 * for standalone features (e.g. the standalone Automation runtime) so their permissions still surface
 * in the central permission UI (the main-screen badge → permissions sheet).
 *
 * Implementations are contributed to a Dagger `Set<PermissionProvider>` via `@IntoSet`. The set may be
 * empty. [requiredPermissions] is queried on every collection pass, so it may return a dynamic list
 * (e.g. only while the feature actually needs the permission).
 */
interface PermissionProvider {

    /** Runtime permission groups currently required. May change over time. */
    fun requiredPermissions(): List<PermissionGroup>
}

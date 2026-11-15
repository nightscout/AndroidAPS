package app.aaps.plugins.automation

import app.aaps.core.interfaces.plugin.PermissionGroup

/**
 * The permission groups a location trigger needs, named the way the platform names them.
 *
 * `PermissionGroup.permissions` is a list of **platform permission strings**, so the values are
 * Android's `Manifest.permission` constants and could not be written in common code. Only the naming
 * is platform-specific though - *whether* they are required is ordinary logic that stays in
 * [AutomationRuntime.requiredPermissions] (a master build, with at least one enabled event using a
 * location trigger).
 *
 * A platform with no such permission model returns an empty list, which is honest rather than silent:
 * the runtime then reports nothing missing, because on that platform nothing is.
 */
fun interface LocationPermissions {

    /** Foreground and background location, as two groups so each can be requested on its own. */
    fun groups(): List<PermissionGroup>
}

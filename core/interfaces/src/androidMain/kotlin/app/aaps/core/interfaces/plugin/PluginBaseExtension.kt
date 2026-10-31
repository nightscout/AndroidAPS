package app.aaps.core.interfaces.plugin

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Returns [PluginBase.requiredPermissions] that are not yet granted.
 * Special permission groups are excluded - they need dedicated checks.
 *
 * This was a member of [PluginBase]. It took a [Context] and asked Android whether a permission is
 * granted, and it was the only thing in that class that touched Android at all - so one method kept
 * the base class every plugin extends, and everything that names it, out of common code. As an
 * extension it lives where it belongs and [PluginBase] itself is platform neutral.
 */
fun PluginBase.missingPermissions(context: Context): List<PermissionGroup> =
    requiredPermissions().filter { group ->
        !group.special && group.permissions.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

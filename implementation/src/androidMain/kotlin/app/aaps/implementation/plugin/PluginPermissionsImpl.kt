package app.aaps.implementation.plugin

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PermissionProvider
import app.aaps.core.interfaces.plugin.PluginPermissions
import app.aaps.core.interfaces.plugin.missingPermissions
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.implementation.R
import app.aaps.core.interfaces.plugin.PermissionGroup
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Which Android permissions AAPS still needs the user to grant.
 *
 * This used to live in [PluginStore], which implemented both this and `ActivePlugin`. Every Android
 * dependency that class had - `Manifest`, `Settings.Secure`, `AlarmManager`, `PowerManager`,
 * `PackageManager` - belonged to this half of it, so pulling the two apart is what let the plugin
 * registry become common code.
 *
 * It stays Android only on purpose rather than for now. Runtime permissions are an Android concept:
 * iOS asks at the point of use and has nothing to enumerate, so there is nothing here to implement
 * there and nothing common that asks for it.
 *
 * The plugin list still comes from [ActivePlugin], since what a plugin requires is the plugin's own
 * business - this only asks each enabled one and checks the answers against the system.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PluginPermissionsImpl(
    private val activePlugin: ActivePlugin,
    private val preferences: Preferences,
    private val permissionProviders: () -> Set<PermissionProvider>
) : PluginPermissions {

    private val plugins get() = activePlugin.getPluginsList()

    companion object {

        /** Custom identifier for the AAPS directory selection requirement. */
        const val PERMISSION_SELECT_DIRECTORY = "app.aaps.permission.SELECT_DIRECTORY"

        /** Custom identifier for notification listener access. */
        const val PERMISSION_NOTIFICATION_LISTENER = "app.aaps.permission.NOTIFICATION_LISTENER"

        private fun isNotificationListenerEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (!TextUtils.isEmpty(flat)) {
                for (name in flat.split(":")) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null && TextUtils.equals(context.packageName, cn.packageName)) return true
                }
            }
            return false
        }
    }

    private fun globalPermissions(context: Context): List<PermissionGroup> = buildList {
        add(
            PermissionGroup(
                permissions = listOf(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS),
                rationaleTitle = TextRef.AndroidRes(R.string.permission_battery_title),
                rationaleDescription = TextRef.AndroidRes(R.string.permission_battery_description),
                special = true,
            )
        )
        add(
            PermissionGroup(
                permissions = listOf(PERMISSION_SELECT_DIRECTORY),
                rationaleTitle = TextRef.AndroidRes(R.string.permission_directory_title),
                rationaleDescription = TextRef.AndroidRes(R.string.permission_directory_description),
                special = true,
                alwaysShowAction = true,
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // When targetSdk < 33, the system won't show a runtime permission dialog for
            // POST_NOTIFICATIONS — must open notification settings directly (special = true).
            // When targetSdk >= 33, the standard runtime dialog works (special = false).
            val needsSettingsWorkaround = context.applicationInfo.targetSdkVersion < Build.VERSION_CODES.TIRAMISU
            add(
                PermissionGroup(
                    permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                    rationaleTitle = TextRef.AndroidRes(R.string.permission_notifications_title),
                    rationaleDescription = TextRef.AndroidRes(R.string.permission_notifications_description),
                    special = needsSettingsWorkaround,
                )
            )
        }
        // Note: USE_FULL_SCREEN_INTENT is intentionally NOT requested. Google Play silently
        // re-revokes it on every update for a sideloaded, non-alarm app, so it can never be relied
        // on. Background alarms instead wake the screen and launch the alarm activity via
        // AlarmManager.setAlarmClock() (see AlarmScreenWakeReceiver / AlarmNotificationManager),
        // which is permission-free.
    }

    private fun isPermissionMissing(context: Context, perm: String): Boolean =
        when (perm) {
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName).not()
            }

            PERMISSION_SELECT_DIRECTORY                              ->
                preferences.getIfExists(StringKey.AapsDirectoryUri).isNullOrEmpty()

            Manifest.permission.SCHEDULE_EXACT_ALARM                 -> {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.canScheduleExactAlarms().not()
            }

            PERMISSION_NOTIFICATION_LISTENER                         ->
                !isNotificationListenerEnabled(context)

            else                                                     ->
                ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }

    override fun collectMissingPermissions(context: Context): List<PermissionGroup> {
        // Standard (non-special) plugin permissions — checked via ContextCompat
        val pluginPerms = plugins.filter { it.isEnabled() }
            .flatMap { it.missingPermissions(context) }
            .distinctBy { it.permissions.toSet() }
        // Special plugin permissions — missingPermissions() skips these, so check separately
        val specialPluginPerms = plugins.filter { it.isEnabled() }
            .flatMap { it.requiredPermissions().filter { group -> group.special } }
            .filter { group -> group.permissions.any { perm -> isPermissionMissing(context, perm) } }
            .distinctBy { it.permissions.toSet() }
        // Global permissions (battery, directory)
        val globalMissing = globalPermissions(context).filter { group ->
            group.permissions.any { perm -> isPermissionMissing(context, perm) }
        }
        // Non-plugin feature permissions (e.g. standalone Automation). Queried dynamically, so a
        // feature only contributes its permission while it actually needs it. isPermissionMissing
        // handles both standard and special permission identifiers.
        val providerMissing = permissionProviders()
            .flatMap { it.requiredPermissions() }
            .filter { group -> group.permissions.any { perm -> isPermissionMissing(context, perm) } }
            .distinctBy { it.permissions.toSet() }
        return (globalMissing + pluginPerms + specialPluginPerms + providerMissing).distinctBy { it.permissions.toSet() }
    }

    override fun collectAllPermissions(context: Context): List<PermissionGroup> =
        (globalPermissions(context) +
            plugins.filter { it.isEnabled() }.flatMap { it.requiredPermissions() } +
            permissionProviders().flatMap { it.requiredPermissions() })
            .distinctBy { it.permissions.toSet() }
}

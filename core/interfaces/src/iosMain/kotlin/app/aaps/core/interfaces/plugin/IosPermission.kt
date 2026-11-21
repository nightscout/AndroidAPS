package app.aaps.core.interfaces.plugin

/**
 * The ids an iOS [PermissionGroup] carries in place of Android's `Manifest.permission` strings.
 *
 * iOS has no list of permission names, but each of these can be read without asking the user, so the
 * permission screen can show it as granted or missing, as on Android. `IosPluginPermissions` reads
 * them. Nothing outside iOS ever sees these ids.
 */
object IosPermission {

    const val BLUETOOTH = "ios.permission.BLUETOOTH"
    const val NOTIFICATIONS = "ios.permission.NOTIFICATIONS"
    const val LOCATION = "ios.permission.LOCATION"
}

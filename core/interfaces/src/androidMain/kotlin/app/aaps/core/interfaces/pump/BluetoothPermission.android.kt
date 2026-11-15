package app.aaps.core.interfaces.pump

import android.Manifest
import android.annotation.SuppressLint
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.plugin.PermissionGroup

/**
 * `InlinedApi` because BLUETOOTH_CONNECT and BLUETOOTH_SCAN are newer than minSdk. Inlining the
 * constants is what the old code did too - the strings are stable and the request is skipped on
 * older versions, where the permissions are granted at install time.
 */
@SuppressLint("InlinedApi")
internal actual fun bluetoothPermissionGroup(): PermissionGroup? = PermissionGroup(
    permissions = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
    rationaleTitle = InterfacesStrings.permission_bluetooth_title,
    rationaleDescription = InterfacesStrings.permission_bluetooth_description,
)

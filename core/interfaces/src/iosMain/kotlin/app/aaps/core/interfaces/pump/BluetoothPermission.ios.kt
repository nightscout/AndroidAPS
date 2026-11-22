package app.aaps.core.interfaces.pump

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.plugin.IosPermission
import app.aaps.core.interfaces.plugin.PermissionGroup

/**
 * iOS asks for Bluetooth itself, the first time the app uses it, but the answer can be read at any
 * time. Listing it lets the permission screen show a refusal. A pump that is refused Bluetooth cannot
 * be reached at all, and without this the only sign was a pairing wizard that waited for ever.
 */
actual fun bluetoothPermissionGroup(): PermissionGroup? = PermissionGroup(
    permissions = listOf(IosPermission.BLUETOOTH),
    rationaleTitle = InterfacesStrings.permission_bluetooth_title,
    rationaleDescription = InterfacesStrings.permission_bluetooth_description,
)

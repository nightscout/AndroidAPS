package app.aaps.core.interfaces.pump

import app.aaps.core.interfaces.plugin.PermissionGroup

/**
 * iOS has no runtime permission to request here. Bluetooth access is declared once in `Info.plist`
 * (`NSBluetoothAlwaysUsageDescription`) and the system prompts on first use, so there is nothing for
 * a plugin to list or for the permission screen to ask for.
 */
internal actual fun bluetoothPermissionGroup(): PermissionGroup? = null

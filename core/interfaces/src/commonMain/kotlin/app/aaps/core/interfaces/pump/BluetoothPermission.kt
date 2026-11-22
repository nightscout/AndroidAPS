package app.aaps.core.interfaces.pump

import app.aaps.core.interfaces.plugin.PermissionGroup

/**
 * The runtime permissions a driver needs to talk to hardware over Bluetooth, or null when the
 * platform has no such thing (the desktop). Pump drivers get it through [PumpPluginBase]; a CGM driver
 * (Eversense) returns it from its own `requiredPermissions`.
 *
 * Android gates BLUETOOTH_CONNECT and BLUETOOTH_SCAN behind a runtime request. iOS asks the user itself
 * on first use, but the answer can be read, so iOS lists it too and the permission screen shows a
 * refusal.
 */
expect fun bluetoothPermissionGroup(): PermissionGroup?

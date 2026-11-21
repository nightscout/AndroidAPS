package app.aaps.core.interfaces.pump

import app.aaps.core.interfaces.plugin.PermissionGroup

/**
 * The runtime permissions a driver needs to talk to hardware over Bluetooth, or null when the
 * platform has no such thing. Pump drivers get it through [PumpPluginBase]; a CGM driver (Eversense)
 * returns it from its own `requiredPermissions`.
 *
 * This is the one part of [PumpPluginBase] that cannot be shared: Android gates BLUETOOTH_CONNECT and
 * BLUETOOTH_SCAN behind a runtime request, while iOS declares its Bluetooth use in `Info.plist` and
 * has no permission list to hand back. Answering null there is the truthful answer - it is not an
 * empty list that happens to work, it is "this platform does not ask".
 */
expect fun bluetoothPermissionGroup(): PermissionGroup?

package app.aaps.core.interfaces.pump

import app.aaps.core.interfaces.plugin.PermissionGroup

/**
 * Desktop has no runtime Bluetooth permission to request, so there is nothing to hand back. Same
 * answer as iOS, and for the same reason: null means "this platform does not ask", not "no
 * permissions needed to talk to a pump".
 */
internal actual fun bluetoothPermissionGroup(): PermissionGroup? = null

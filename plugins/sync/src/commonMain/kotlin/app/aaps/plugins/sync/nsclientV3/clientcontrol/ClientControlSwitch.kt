package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences

/**
 * Stores the master's client-control switch ([BooleanKey.HardenedMode]) with the value in use, if
 * nothing is stored yet.
 *
 * Unset, the switch is on only in simple mode. So with paired clients, leaving simple mode later would
 * silently turn client control and hardened mode off. This stores the value in use, it does not change
 * it. Called when a client is paired, and at master start for clients paired before that was done.
 */
internal fun Preferences.storeClientControlSwitchIfUnset() {
    if (getIfExists(BooleanKey.HardenedMode) == null) put(BooleanKey.HardenedMode, get(BooleanKey.HardenedMode))
}

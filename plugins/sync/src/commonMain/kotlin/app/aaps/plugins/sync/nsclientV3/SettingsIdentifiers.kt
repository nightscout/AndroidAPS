package app.aaps.plugins.sync.nsclientV3

import app.aaps.plugins.sync.nsclientV3.SettingsIdentifiers.COLD
import app.aaps.plugins.sync.nsclientV3.SettingsIdentifiers.STATE

/**
 * NS `settings` collection document identifiers used by AAPS running-config sync.
 *
 * The running configuration is split across two docs by write-frequency, so a frequent
 * runtime change (scene start/stop) does not re-upload the whole rarely-changing config:
 *  - [COLD]: plugin config, overview, definitions, authorized clients — published on user edits.
 *  - [STATE]: active scene + computed runtime flags — published on scene lifecycle (short debounce).
 *
 * [STATE] deliberately does not start with [app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlPublisher.IDENTIFIER_PREFIX]
 * so the WS dispatcher can demux the three doc kinds by identifier.
 */
object SettingsIdentifiers {

    const val COLD = "aaps"
    const val STATE = "aaps-state"
}

package app.aaps.core.interfaces.alerts

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.keys.interfaces.TextRef

/**
 * The kinds raised from what Nightscout sends (source [SOURCE], `_docs/ALARMS.md`, step 3). The ids are stored with
 * the user's choice: never change one.
 *
 * Nightscout's own alarms of level 1 and 2 are not here: they were removed, as every phone has its own BG alarms.
 */
enum class NightscoutAlert(private val idName: String, override val title: TextRef) : AlertKind {

    /** A careportal announcement: one chime and a card, ended by the user's answer. */
    ANNOUNCEMENT("announcement", InterfacesStrings.alert_kind_ns_announcement);

    // A getter: the companion object is not there yet while the entries are built
    override val id: String get() = "$SOURCE.$idName"
    override val source: String get() = SOURCE
    override val origin: AlertOrigin get() = AlertOrigin.NIGHTSCOUT
    override val isProtected: Boolean get() = false

    // Its record is in Nightscout already: written again, it would come back as a new announcement
    override val announced: Boolean get() = false

    companion object {

        /** The source every alert from Nightscout is raised under. */
        const val SOURCE = "nightscout"
    }
}

/**
 * Raises a careportal announcement as a [NightscoutAlert.ANNOUNCEMENT]. Its own interface, and not [Alerts] itself,
 * so the Nightscout client works also on a platform with no alert core (the desktop), where it is absent.
 */
interface NightscoutAnnouncements {

    /** The announcement written at [timestamp] with the text [note]: one chime and a card, ended by the user. */
    fun announce(timestamp: Long, note: String)
}

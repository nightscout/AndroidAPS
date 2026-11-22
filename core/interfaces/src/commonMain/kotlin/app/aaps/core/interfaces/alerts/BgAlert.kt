package app.aaps.core.interfaces.alerts

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.keys.interfaces.TextRef

/**
 * The kinds of the core's own BG alarms (source [SOURCE]), evaluated on every stored reading on the master and on
 * every client (`_docs/ALARMS.md`, step 2). The ids are stored with the user's choice: never change one.
 */
enum class BgAlert(private val idName: String, override val title: TextRef, override val isProtected: Boolean) : AlertKind {

    /** Always on, always rings, and also in Do Not Disturb. The user sets only its threshold. */
    URGENT_LOW("urgent_low", InterfacesStrings.alert_kind_bg_urgent_low, isProtected = true);

    // The kinds of the user's own low and high rules come with those rules (step 2a-2).

    // A getter: the companion object is not there yet while the entries are built
    override val id: String get() = "$SOURCE.$idName"
    override val source: String get() = SOURCE
    override val origin: AlertOrigin get() = AlertOrigin.APP

    companion object {

        /** The source every BG alarm is raised under. */
        const val SOURCE = "bg"
    }
}

/**
 * The BG alarms: watches the stored readings and raises or ends the level alerts. Started once by the app start; a
 * platform without an alert core (the desktop) has none.
 */
interface BgAlarms {

    /** Starts watching the readings and the rules. Called once, after the app start. */
    fun start()
}

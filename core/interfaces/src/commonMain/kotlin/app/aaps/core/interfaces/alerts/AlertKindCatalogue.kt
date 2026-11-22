package app.aaps.core.interfaces.alerts

import app.aaps.core.keys.interfaces.TextRef

/**
 * The kinds one raiser can raise, shown as one group in the alert settings (`_docs/ALARMS.md`, section 6). The app
 * has one, and each driver with kinds of its own adds one.
 */
interface AlertKindCatalogue {

    /** The group's heading: the app, or the driver's name. */
    val title: TextRef

    val kinds: List<AlertKind>

    /** Whether the group is shown now: a driver's while it is in use, the app's always. */
    fun inUse(): Boolean
}

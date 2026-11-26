package app.aaps.core.interfaces.alerts

import app.aaps.core.keys.interfaces.TextRef

/**
 * The kinds one raiser can raise (`_docs/ALARMS.md`, section 6). The app has one, and each driver with kinds of its
 * own adds one. The settings show its kinds in their sections and groups ([AlertKind.section], [AlertKind.group]).
 */
interface AlertKindCatalogue {

    /** The raiser's name: the app, or the driver's. A driver's is shown under the heading of its pump or CGM section. */
    val title: TextRef

    val kinds: List<AlertKind>

    /** Whether its kinds are in the settings now: a driver's while it is in use, the app's always. */
    fun inUse(): Boolean
}

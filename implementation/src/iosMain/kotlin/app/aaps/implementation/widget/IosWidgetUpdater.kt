package app.aaps.implementation.widget

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.widget.WidgetUpdater
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * There is no home screen widget on iOS yet, so there is nothing to redraw.
 *
 * Unlike the other absent capabilities this one is harmless: a widget that does not refresh shows
 * stale glucose on the home screen, and nothing in AAPS acts on the redraw. It is not in the same
 * class as scene expiry, where the missing callback would leave therapy settings applied.
 *
 * When a WidgetKit extension exists this becomes one line - `WidgetCenter.shared.reloadAllTimelines()`
 * from the app target, since WidgetKit is not exposed to Kotlin/Native. Until then it logs, so a
 * developer wondering why nothing updates finds an answer rather than silence.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosWidgetUpdater @Inject constructor(
    private val aapsLogger: AAPSLogger
) : WidgetUpdater {

    override fun update(from: String) {
        aapsLogger.debug(LTag.WIDGET, "No iOS widget to update, ignoring refresh from $from")
    }
}

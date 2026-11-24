package app.aaps.plugins.eversense.alerts

import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertKindCatalogue
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.eversense.EversensePlugin
import app.aaps.plugins.eversense.EversenseStrings
import dev.zacsweers.metro.Inject

/** The transmitter's kinds in the alert settings, shown while Eversense is the BG source. */
@Inject
class EversenseAlertCatalogue(private val activePlugin: ActivePlugin) : AlertKindCatalogue {

    override val title: TextRef = EversenseStrings.source_eversense
    override val kinds: List<AlertKind> = EversenseAlertKind.all
    override fun inUse(): Boolean = activePlugin.activeBgSource is EversensePlugin
}

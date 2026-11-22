package app.aaps.pump.carelevo.common

import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertKindCatalogue
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.carelevo.CarelevoPumpPlugin
import app.aaps.pump.carelevo.CarelevoStrings
import dev.zacsweers.metro.Inject

/** The patch's kinds in the alert settings, shown while Carelevo is the pump. */
@Inject
class CarelevoAlertCatalogue(private val activePlugin: ActivePlugin) : AlertKindCatalogue {

    override val title: TextRef = CarelevoStrings.carelevo
    override val kinds: List<AlertKind> = CarelevoAlertKind.all
    override fun inUse(): Boolean = activePlugin.activePumpInternal is CarelevoPumpPlugin
}

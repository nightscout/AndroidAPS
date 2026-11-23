package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertKindCatalogue
import app.aaps.core.interfaces.alerts.NightscoutAlert
import app.aaps.core.keys.interfaces.TextRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** The alerts from Nightscout in the alert settings, shown while the Nightscout client is on. */
@ContributesIntoSet(AppScope::class, binding = binding<AlertKindCatalogue>())
@Inject
class NightscoutAlertCatalogue(
    // A provider: the plugin reaches much of the app, and the alert core reads its catalogues early
    private val nsClientV3Plugin: () -> NSClientV3Plugin
) : AlertKindCatalogue {

    override val title: TextRef = InterfacesStrings.alert_kinds_group_nightscout
    override val kinds: List<AlertKind> = NightscoutAlert.entries
    override fun inUse(): Boolean = nsClientV3Plugin().isEnabled()
}

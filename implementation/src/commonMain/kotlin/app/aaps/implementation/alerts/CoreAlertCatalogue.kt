package app.aaps.implementation.alerts

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertKindCatalogue
import app.aaps.core.interfaces.alerts.CoreAlert
import app.aaps.core.keys.interfaces.TextRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** The app's own kinds in the alert settings, always shown. */
@ContributesIntoSet(AppScope::class, binding = binding<AlertKindCatalogue>())
@Inject
class CoreAlertCatalogue : AlertKindCatalogue {

    override val title: TextRef = InterfacesStrings.alert_kinds_group_app
    override val kinds: List<AlertKind> = CoreAlert.entries
    override fun inUse(): Boolean = true
}

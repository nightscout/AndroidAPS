package app.aaps.ui.compose.alertKinds

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/** The desktop has alerts (`AlertsDesktopBindings`), so it has the list of alert kinds. */
@ContributesTo(AppScope::class)
@BindingContainer
object AlertKindsDesktopBindings {

    @Provides
    fun alertKindsSettings(impl: AlertKindsSettingsImpl): AlertKindsSettings = impl
}

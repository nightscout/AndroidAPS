package app.aaps.ui.compose.alertKinds

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/** The desktop has no alert core, so it shows no alert kinds: no settings item, no line in the sheet. */
@ContributesTo(AppScope::class)
@BindingContainer
object AlertKindsDesktopBindings {

    @Provides
    fun alertKindsSettings(): AlertKindsSettings = AbsentAlertKindsSettings
}

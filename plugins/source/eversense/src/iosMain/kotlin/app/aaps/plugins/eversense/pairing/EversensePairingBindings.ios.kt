package app.aaps.plugins.eversense.pairing

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** iOS has no Eversense driver yet: the pairing answers that nothing is paired (see [NoEversenseDriver]). */
@ContributesTo(AppScope::class)
@BindingContainer
object EversensePairingBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun pairing(): EversensePairing = NoEversenseDriver()
}

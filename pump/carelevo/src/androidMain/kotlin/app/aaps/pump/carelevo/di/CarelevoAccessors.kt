package app.aaps.pump.carelevo.di

import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.pump.carelevo.CarelevoPumpPlugin
import app.aaps.pump.carelevo.common.CarelevoPatch
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

/**
 * What an instrumented test needs from the CareLevo driver. Read from the app's own graph rather than built by the
 * test, for the reasons `DanaAccessors` in `:pump:dana:common` gives, and declared here so that removing
 * `:pump:carelevo` from `settings.gradle` takes it with it.
 *
 * [carelevoBleCentral] is where a test sees whether it got the emulator, and reaches the emulated patch's test hooks.
 */
@ContributesTo(AppScope::class)
interface CarelevoAccessors {

    val carelevoPumpPlugin: CarelevoPumpPlugin
    val carelevoPatch: CarelevoPatch

    @CarelevoCentral
    val carelevoBleCentral: BleCentral
}

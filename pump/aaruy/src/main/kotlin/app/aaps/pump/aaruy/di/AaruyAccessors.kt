package app.aaps.pump.aaruy.di

import app.aaps.pump.aaruy.AaruyPlugin
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.services.AaruyBLE
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

/**
 * What an instrumented test needs from the Aaruy driver. Read from the app's own graph rather than built by the test,
 * for the reasons `DanaAccessors` in `:pump:dana:common` gives, and declared here so that removing `:pump:aaruy` from
 * `settings.gradle` takes it with it.
 *
 * [aaruyBle] has the emulator transport when the emulator is active, and with it its test hooks.
 */
@ContributesTo(AppScope::class)
interface AaruyAccessors {

    val aaruyPlugin: AaruyPlugin
    val aaruyBle: AaruyBLE
    val aaruyPump: AaruyPump
}

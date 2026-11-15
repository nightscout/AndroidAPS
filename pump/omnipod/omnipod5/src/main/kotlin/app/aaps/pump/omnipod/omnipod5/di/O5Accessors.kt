package app.aaps.pump.omnipod.omnipod5.di

import app.aaps.pump.omnipod.omnipod5.O5PumpPlugin
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.O5Radio
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

/**
 * What an instrumented test needs from the Omnipod 5 driver. Read from the app's own graph rather than built by the
 * test, for the reasons `DanaAccessors` in `:pump:dana:common` gives, and declared here so that removing
 * `:pump:omnipod:omnipod5` from `settings.gradle` takes it with it.
 *
 * [o5Radio] is where a test sees whether it got the emulator, and reaches its test hooks.
 */
@ContributesTo(AppScope::class)
interface O5Accessors {

    val o5PumpPlugin: O5PumpPlugin
    val o5PodStateManager: O5PodStateManager
    val o5Radio: O5Radio
}

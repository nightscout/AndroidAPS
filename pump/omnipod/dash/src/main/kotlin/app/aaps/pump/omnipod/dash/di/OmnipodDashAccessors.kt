package app.aaps.pump.omnipod.dash.di

import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.dash.OmnipodDashPumpPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

/**
 * What an instrumented test needs from the Omnipod Dash driver. Read from the app's own graph rather than built by the
 * test, for the reasons `DanaAccessors` in `:pump:dana:common` gives, and declared here so that removing
 * `:pump:omnipod:dash` from `settings.gradle` takes it with it.
 *
 * [dashBleConnectionFactory] is where a test sees whether it got the emulator, and reaches its test hooks.
 */
@ContributesTo(AppScope::class)
interface OmnipodDashAccessors {

    val omnipodDashPumpPlugin: OmnipodDashPumpPlugin
    val dashPodStateManager: OmnipodDashPodStateManager
    val dashBleConnectionFactory: BleConnectionFactory
}

package app.aaps.di.pump

import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodDashBleManager
import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodDashBleManagerImpl
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.LegacyBleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.LegacyBleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManagerImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Omnipod BLE and pod state, owned by Metro from `:app`.
 * Hand-written `@Provides` rather than `@ContributesBinding` on the classes, and deliberately kept that
 * way. The four bindings and the scope that matters are one short file you can read at once, instead of
 * eight annotations spread over four files in another module. Nothing tests the scoping either way -
 * `ContributedBindingsTest` asserts by hand on graph accessors, so contributing these would not put
 * them under a guard - and the failure the scope prevents is silent, so visibility is worth more here
 * than the few lines it costs.
 * `@SingleIn(AppScope::class)` on every provider. These hold live connection and pod state - a second
 * `OmnipodDashPodStateManagerImpl` would mean the driver and the UI reading different pods - so the
 * scope is the point, not an optimisation.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object OmnipodCommonBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideOmnipodDashPodStateManager(
        logger: AAPSLogger,
        rxBus: RxBus,
        preferences: Preferences,
        config: Config
    ): OmnipodDashPodStateManager = OmnipodDashPodStateManagerImpl(logger, rxBus, preferences, config)

    @Provides
    @SingleIn(AppScope::class)
    fun provideBleDeviceManager(
        context: Context,
        aapsLogger: AAPSLogger,
        preferences: Preferences
    ): BleDeviceManager = LegacyBleDeviceManager(context, aapsLogger, preferences)

    @Provides
    @SingleIn(AppScope::class)
    fun provideBleConnectionFactory(
        context: Context,
        aapsLogger: AAPSLogger,
        config: Config,
        podState: OmnipodDashPodStateManager
    ): BleConnectionFactory = LegacyBleConnectionFactory(context, aapsLogger, config, podState)

    @Provides
    @SingleIn(AppScope::class)
    fun provideOmnipodDashBleManager(
        aapsLogger: AAPSLogger,
        podState: OmnipodDashPodStateManager,
        config: Config,
        bleConnectionFactory: BleConnectionFactory,
        bleDeviceManager: BleDeviceManager
    ): OmnipodDashBleManager = OmnipodDashBleManagerImpl(aapsLogger, podState, config, bleConnectionFactory, bleDeviceManager)
}

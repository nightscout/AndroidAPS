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
 *
 * Hand-written `@Provides` rather than annotations on the classes, because `:pump:omnipod:common` does
 * not apply the Metro plugin - there is no Kotlin IR for Metro to read an `@Inject` constructor from,
 * so it cannot generate a factory. It does not have to: a provider that calls the constructor itself
 * needs no factory, which is the same shape [ErosJavaBindings] uses for the Java eros classes. Adding
 * the plugin to `:pump:omnipod:common` would work too and is the tidier end state; this keeps the
 * change inside `:app`.
 *
 * `@SingleIn(AppScope::class)` on every provider. These hold live connection and pod state - a second
 * `OmnipodDashPodStateManagerImpl` would mean the driver and the UI reading different pods - so the
 * scope is the point, not an optimisation.
 *
 * This replaces `OmnipodCommonBleModule` and the two interface `@Binds` of `OmnipodDashModule`, which
 * were Dagger modules in `:app` for the same "the pump module has no Dagger processor" reason.
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

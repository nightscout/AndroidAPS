package app.aaps.pump.omnipod.dash.di

import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodBleManager
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
 * Dash BLE and pod state, owned by Metro from `:app`.
 * The state and connection providers are scoped because the driver and UI must share the same live pod state.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object OmnipodDashBindings {

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
    fun provideOmnipodBleManager(
        aapsLogger: AAPSLogger,
        podState: OmnipodDashPodStateManager,
        config: Config,
        bleConnectionFactory: BleConnectionFactory,
        bleDeviceManager: BleDeviceManager
    ): OmnipodBleManager = OmnipodDashBleManagerImpl(aapsLogger, podState, config, bleConnectionFactory, bleDeviceManager)
}

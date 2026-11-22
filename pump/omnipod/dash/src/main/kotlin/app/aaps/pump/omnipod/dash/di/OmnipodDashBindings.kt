package app.aaps.pump.omnipod.dash.di

import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
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
import app.aaps.pump.omnipod.emulator.OmnipodEmulatorTransport
import app.aaps.pump.omnipod.emulator.PodEmulator
import app.aaps.pump.omnipod.emulator.PodLinkState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Dash BLE and pod state, owned by Metro from `:app`.
 * The state and connection providers are scoped because the driver and UI must share the same live pod state.
 *
 * The radio is the real one, or the in-tree emulator when the `emulate_omnipod_dash` option is on. The emulator takes
 * the place of the two radio interfaces only ([BleDeviceManager] and [BleConnectionFactory]); the driver above them
 * is the same, so it cannot tell an emulated pod from a real one.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object OmnipodDashBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideOmnipodDashPodStateManager(
        logger: AAPSLogger,
        rxBus: RxBus,
        preferences: Preferences
    ): OmnipodDashPodStateManager = OmnipodDashPodStateManagerImpl(logger, rxBus, preferences)

    /**
     * The emulated pod with its radio. Made only when something asks for it, which is when the option is on.
     * The pod itself lives in [PumpEmulators]: it is the same pod after a test restarts the app, as a real one would be.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideOmnipodEmulatorTransport(
        aapsLogger: AAPSLogger,
        config: Config,
        pumpEmulators: PumpEmulators,
        podState: OmnipodDashPodStateManager
    ): OmnipodEmulatorTransport {
        aapsLogger.debug(LTag.PUMPEMULATOR, "Omnipod Dash emulator active - real Bluetooth is not used")
        return OmnipodEmulatorTransport(
            emulator = pumpEmulators.getOrCreate(PodEmulator.KEY) { PodEmulator(aapsLogger) },
            aapsLogger = aapsLogger,
            config = config,
            // As `Connection` of the real radio does: the driver reads from the pod state whether the link is up.
            onLinkState = { state ->
                when (state) {
                    PodLinkState.CONNECTING   -> {
                        podState.connectionAttempts++
                        podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTING
                    }

                    PodLinkState.CONNECTED    -> podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTED
                    PodLinkState.DISCONNECTED -> podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
                }
            }
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideBleDeviceManager(
        context: Context,
        aapsLogger: AAPSLogger,
        preferences: Preferences,
        config: Config,
        emulatorTransport: () -> OmnipodEmulatorTransport
    ): BleDeviceManager =
        if (config.isEnabled(ExternalOptions.EMULATE_OMNIPOD_DASH)) emulatorTransport()
        else LegacyBleDeviceManager(context, aapsLogger, preferences)

    @Provides
    @SingleIn(AppScope::class)
    fun provideBleConnectionFactory(
        context: Context,
        aapsLogger: AAPSLogger,
        config: Config,
        podState: OmnipodDashPodStateManager,
        emulatorTransport: () -> OmnipodEmulatorTransport
    ): BleConnectionFactory =
        if (config.isEnabled(ExternalOptions.EMULATE_OMNIPOD_DASH)) emulatorTransport()
        else LegacyBleConnectionFactory(context, aapsLogger, config, podState)

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

package app.aaps.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.carelevo.ble.CarelevoBleTransport
import app.aaps.pump.carelevo.ble.CarelevoBleTransportImpl
import app.aaps.pump.carelevo.emulator.CarelevoEmulatorBleTransport
import app.aaps.pump.carelevo.emulator.CarelevoPumpEmulator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class CarelevoModules {

    /**
     * Selects the emulated patch over the real Bluetooth stack when the `emulate_carelevo` marker
     * file is present, so the driver can be exercised end-to-end without patch hardware.
     */
    @Provides
    @Singleton
    fun provideCarelevoBleTransport(
        config: Config,
        carelevoBleTransportImpl: CarelevoBleTransportImpl,
        aapsLogger: AAPSLogger
    ): CarelevoBleTransport =
        if (config.isEnabled(ExternalOptions.EMULATE_CARELEVO)) {
            aapsLogger.debug(LTag.PUMPEMULATOR, "CareLevo emulator active — real Bluetooth is not used")
            val emulator = CarelevoPumpEmulator(aapsLogger = aapsLogger)
            // Debug-only alarm scenarios, each its own marker file next to `emulate_carelevo` itself —
            // lets an alarm be reproduced on demand (via the emulation-only "re-check alarm snapshot"
            // button in CarelevoOverviewScreen) without real hardware. Critical and advisory are kept
            // independent because the same condition resolves to a different severity on each: critical
            // resolves to a WARNING cause (auto-discards the patch), advisory to the ALERT cause of the
            // same condition (a plain user-clearable alarm) — see CarelevoPumpState.activeAlarmFlags.
            emulator.state.criticalAlarmFlags = emulator.state.criticalAlarmFlags.copy(
                lowBattery = config.isEnabled(ExternalOptions.EMULATE_CARELEVO_LOW_BATTERY),
                occlusionDetected = config.isEnabled(ExternalOptions.EMULATE_CARELEVO_OCCLUSION)
            )
            emulator.state.advisoryAlarmFlags = emulator.state.advisoryAlarmFlags.copy(
                lowBattery = config.isEnabled(ExternalOptions.EMULATE_CARELEVO_LOW_BATTERY_ALERT),
                outOfRangeTemperature = config.isEnabled(ExternalOptions.EMULATE_CARELEVO_INVALID_TEMPERATURE)
            )
            CarelevoEmulatorBleTransport(
                emulator = emulator,
                aapsLogger = aapsLogger
            )
        } else {
            carelevoBleTransportImpl
        }
}

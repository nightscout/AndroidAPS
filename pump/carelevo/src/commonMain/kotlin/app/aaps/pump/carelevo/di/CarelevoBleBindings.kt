package app.aaps.pump.carelevo.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.ble.EmulatedBleCentral
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.pump.carelevo.emulator.CarelevoEmulatorPeripheral
import app.aaps.pump.carelevo.emulator.CarelevoPumpEmulator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

/**
 * The Bluetooth central the CareLevo driver talks through: the phone's, or an emulated one that holds the emulated
 * patch. Only the driver gets the emulated one; the other pumps keep the phone's.
 */
@Qualifier
annotation class CarelevoCentral

/**
 * The Bluetooth binding of CareLevo. It lives in the driver rather than in the app's `withPumps` source set, because
 * `:pump:carelevo:emulator` sits on `:pump:carelevo:protocol` rather than on the driver, so the driver can depend on
 * the emulator without a cycle. The app names no CareLevo type at all.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object CarelevoBleBindings {

    /**
     * The emulated patch when the `emulate_carelevo` marker file is there, else the phone's Bluetooth. That way the
     * driver can be run end to end without a patch.
     */
    @Provides
    @SingleIn(AppScope::class)
    @CarelevoCentral
    fun provideCarelevoCentral(
        config: Config,
        phone: BleCentral,
        aapsLogger: AAPSLogger,
        pumpEmulators: PumpEmulators
    ): BleCentral =
        if (config.isEnabled(ExternalOptions.EMULATE_CARELEVO)) {
            aapsLogger.debug(LTag.PUMPEMULATOR, "CareLevo emulator active - real Bluetooth is not used")
            // The same patch after a test restarts the app: it runs on, as a real one would. The radio is new.
            val emulator = pumpEmulators.getOrCreate(CarelevoPumpEmulator.KEY) {
                CarelevoPumpEmulator(aapsLogger = aapsLogger).also { emulator ->
                    // Debug-only alarm scenarios, each its own marker file next to `emulate_carelevo` itself -
                    // lets an alarm be reproduced on demand (via the emulation-only "re-check alarm snapshot"
                    // button in CarelevoOverviewScreen) without real hardware. Critical and advisory are kept
                    // independent because the same condition resolves to a different severity on each: critical
                    // resolves to a WARNING cause (auto-discards the patch), advisory to the ALERT cause of the
                    // same condition (a plain user-clearable alarm) - see CarelevoPumpState.activeAlarmFlags.
                    emulator.state.criticalAlarmFlags = emulator.state.criticalAlarmFlags.copy(
                        lowBattery = config.isEnabled(ExternalOptions.EMULATE_CARELEVO_LOW_BATTERY),
                        occlusionDetected = config.isEnabled(ExternalOptions.EMULATE_CARELEVO_OCCLUSION)
                    )
                    emulator.state.advisoryAlarmFlags = emulator.state.advisoryAlarmFlags.copy(
                        lowBattery = config.isEnabled(ExternalOptions.EMULATE_CARELEVO_LOW_BATTERY_ALERT),
                        outOfRangeTemperature = config.isEnabled(ExternalOptions.EMULATE_CARELEVO_INVALID_TEMPERATURE)
                    )
                }
            }
            // Bonded, as the emulated patch always was: the pairing bonds only a patch that is not.
            EmulatedBleCentral(aapsLogger).also { it.add(CarelevoEmulatorPeripheral(emulator, aapsLogger), bonded = true) }
        } else {
            phone
        }
}

package app.aaps.pump.aaruy.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.ble.EmulatedBleCentral
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.pump.aaruy.comm.aaruyFrameKey
import app.aaps.pump.aaruy.emulator.AaruyEmulatorPeripheral
import app.aaps.pump.aaruy.emulator.AaruyPumpEmulator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

/**
 * The Bluetooth central the Aaruy driver talks through: the phone's, or an emulated one that holds the emulated pump.
 * Only the driver gets the emulated one; the other pumps keep the phone's.
 */
@Qualifier
annotation class AaruyCentral

@ContributesTo(AppScope::class)
@BindingContainer
object AaruyBleBindings {

    /** The emulated pump when the `emulate_aaruy` file is in the AAPS extra folder, otherwise the phone's Bluetooth. */
    @Provides
    @SingleIn(AppScope::class)
    @AaruyCentral
    fun provideAaruyCentral(config: Config, phone: BleCentral, aapsLogger: AAPSLogger, pumpEmulators: PumpEmulators): BleCentral =
        if (config.isEnabled(ExternalOptions.EMULATE_AARUY)) {
            aapsLogger.warn(LTag.PUMPEMULATOR, "Aaruy pump EMULATOR is active. No insulin is delivered.")
            // The same pump after a test restarts the app: it runs on, as a real one would. The radio is new.
            val emulator = pumpEmulators.getOrCreate(AaruyPumpEmulator.KEY) { AaruyPumpEmulator(keyProvider = { aaruyFrameKey() }) }
            // Bonded, as the emulated pump always was. The emulator still checks the PIN of a new bond.
            EmulatedBleCentral(aapsLogger).also { it.add(AaruyEmulatorPeripheral(emulator), bonded = true) }
        } else {
            phone
        }
}

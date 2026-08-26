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
            CarelevoEmulatorBleTransport(
                emulator = CarelevoPumpEmulator(aapsLogger = aapsLogger),
                aapsLogger = aapsLogger
            )
        } else {
            carelevoBleTransportImpl
        }
}

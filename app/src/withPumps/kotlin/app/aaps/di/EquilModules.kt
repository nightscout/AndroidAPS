package app.aaps.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.ble.EquilBleTransport
import app.aaps.pump.equil.ble.EquilBleTransportImpl
import app.aaps.pump.equil.emulator.EquilEmulatorBleTransport
import app.aaps.pump.equil.emulator.EquilPumpEmulator
import app.aaps.pump.equil.keys.EquilStringKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class EquilModules {

    @Provides
    @Singleton
    fun provideEquilBleTransport(
        config: Config,
        equilBleTransportImpl: EquilBleTransportImpl,
        aapsLogger: AAPSLogger,
        preferences: Preferences
    ): EquilBleTransport {
        return if (config.isEnabled(ExternalOptions.EMULATE_EQUIL)) {
            val pumpEmulator = EquilPumpEmulator(aapsLogger = aapsLogger)
            EquilEmulatorBleTransport(
                emulator = pumpEmulator,
                aapsLogger = aapsLogger,
                serialNumberProvider = {
                    val sn = "A${String.format("%05d", (0..99999).random())}"
                    preferences.put(EquilStringKey.EmulatorDeviceName, "Equil - $sn")
                    aapsLogger.debug(LTag.PUMPCOMM, "Equil emulator scan with serial: $sn")
                    sn
                },
                storedPasswordProvider = {
                    // Read the device password stored by CmdPair after pairing
                    val pwd = preferences.get(EquilStringKey.Password)
                    if (pwd.isNotEmpty()) pwd else null
                }
            )
        } else {
            equilBleTransportImpl
        }
    }
}

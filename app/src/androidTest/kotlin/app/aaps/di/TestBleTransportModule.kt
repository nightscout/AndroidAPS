package app.aaps.di

import app.aaps.pump.danars.emulator.EmulatorBleTransport
import app.aaps.pump.danars.services.BleTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

/**
 * Replaces [app.aaps.pump.danars.di.DanaRSBleTransportModule] for instrumented tests.
 * Provides [EmulatorBleTransport] as the [BleTransport] implementation so that DanaRS
 * communication can be tested without real Bluetooth hardware.
 */
@Module
@DisableInstallInCheck
class TestBleTransportModule {

    @Provides @Singleton
    fun provideBleTransport(): BleTransport = EmulatorBleTransport()

    @Provides @Singleton
    fun provideEmulatorBleTransport(bleTransport: BleTransport): EmulatorBleTransport =
        bleTransport as EmulatorBleTransport
}

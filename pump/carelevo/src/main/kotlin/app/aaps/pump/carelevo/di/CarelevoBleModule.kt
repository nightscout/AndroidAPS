package app.aaps.pump.carelevo.di

import app.aaps.pump.carelevo.config.BleEnvConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Named

/**
 * GATT identifiers for the CareLevo transport.
 *
 * The [app.aaps.pump.carelevo.ble.CarelevoBleTransport] binding itself lives in the app's
 * `withPumps` source set (`CarelevoModules`), so the real impl and the emulator can be selected
 * per build — the same arrangement Dana and Equil use.
 */
@Module
@InstallIn(SingletonComponent::class)
class CarelevoBleModule {

    @Provides
    @Named("cccDescriptor")
    internal fun provideCccDescriptor(): UUID = UUID.fromString(BleEnvConfig.BLE_CCC_DESCRIPTOR)

    @Provides
    @Named("serviceUuid")
    internal fun provideServiceUuid(): UUID = UUID.fromString(BleEnvConfig.BLE_SERVICE_UUID)

    @Provides
    @Named("characterTx")
    internal fun provideTxCharacteristicUuid(): UUID = UUID.fromString(BleEnvConfig.BLE_TX_CHAR_UUID)

    @Provides
    @Named("characterRx")
    internal fun provideRxCharacteristicUuid(): UUID = UUID.fromString(BleEnvConfig.BLE_RX_CHAR_UUID)
}

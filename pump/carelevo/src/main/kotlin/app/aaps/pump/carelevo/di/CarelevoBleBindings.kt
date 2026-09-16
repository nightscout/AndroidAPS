package app.aaps.pump.carelevo.di

import app.aaps.pump.carelevo.config.BleEnvConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import java.util.UUID

/**
 * The GATT characteristic AAPS writes commands to (the patch receives on it).
 */
@Qualifier
annotation class CarelevoRxCharacteristic

/**
 * The GATT characteristic the patch sends notifications on (the patch transmits on it).
 */
@Qualifier
annotation class CarelevoTxCharacteristic

/**
 * GATT identifiers for the CareLevo transport.
 *
 * Only the two characteristics `CarelevoBleSession` talks over are bound here. The service and
 * descriptor ids are read straight from [BleEnvConfig] by
 * `app.aaps.pump.carelevo.ble.CarelevoBleTransportImpl`, so they need no binding.
 *
 * The [app.aaps.pump.carelevo.ble.CarelevoBleTransport] binding itself lives in the app's
 * `withPumps` source set (`CarelevoTransportBindings`), so the real impl and the emulator can be
 * selected per build - the same arrangement Dana and Equil use.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object CarelevoBleBindings {

    @Provides
    @CarelevoRxCharacteristic
    fun provideRxCharacteristicUuid(): UUID = UUID.fromString(BleEnvConfig.BLE_RX_CHAR_UUID)

    @Provides
    @CarelevoTxCharacteristic
    fun provideTxCharacteristicUuid(): UUID = UUID.fromString(BleEnvConfig.BLE_TX_CHAR_UUID)
}

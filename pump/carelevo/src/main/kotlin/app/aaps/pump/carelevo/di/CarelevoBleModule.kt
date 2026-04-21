package app.aaps.pump.carelevo.di

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.carelevo.ble.core.CarelevoBleController
import app.aaps.pump.carelevo.ble.core.CarelevoBleControllerImpl
import app.aaps.pump.carelevo.ble.core.CarelevoBleManager
import app.aaps.pump.carelevo.ble.core.CarelevoBleMangerImpl
import app.aaps.pump.carelevo.ble.data.BleParams
import app.aaps.pump.carelevo.ble.data.ConfigParams
import app.aaps.pump.carelevo.config.BleEnvConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Named
import javax.inject.Singleton

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

    @Provides
    internal fun provideConfigParams() = ConfigParams(isForeground = true)

    @Provides
    internal fun provideBleParams(
        @Named("cccDescriptor") cccd: UUID,
        @Named("serviceUuid") serviceUuid: UUID,
        @Named("characterTx") tx: UUID,
        @Named("characterRx") rx: UUID
    ) = BleParams(cccd, serviceUuid, tx, rx)

    @Provides
    @Singleton
    internal fun provideCarelevoBleManager(
        context: Context,
        param: BleParams,
        aapsLogger: AAPSLogger
    ): CarelevoBleManager {
        return CarelevoBleMangerImpl(
            context,
            param,
            aapsLogger
        )
    }

    @Provides
    @Singleton
    internal fun provideCarelevoBleController(
        param: BleParams,
        btManager: CarelevoBleManager
    ): CarelevoBleController {
        return CarelevoBleControllerImpl(
            param,
            btManager
        )
    }
}

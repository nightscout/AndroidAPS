package app.aaps.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.dana.di.DanaHistoryModule
import app.aaps.pump.dana.di.DanaModule
import app.aaps.pump.dana.keys.DanaStringKey
import app.aaps.pump.danar.di.DanaRModule
import app.aaps.pump.danars.di.DanaRSModule
import app.aaps.pump.danars.emulator.EmulatorBleTransport
import app.aaps.pump.danars.emulator.NotificationPumpDisplay
import app.aaps.pump.danars.encryption.EncryptionType
import app.aaps.pump.danars.services.BleTransport
import app.aaps.pump.danars.services.BleTransportImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module(
    includes = [
        DanaHistoryModule::class,
        DanaModule::class,
        DanaRModule::class,
        DanaRSModule::class,
    ]
)
@InstallIn(SingletonComponent::class)
class DanaModules {

    @Provides
    @Singleton
    fun provideBleTransport(
        config: Config,
        bleTransportImpl: BleTransportImpl,
        notificationManager: NotificationManager,
        aapsLogger: AAPSLogger,
        preferences: Preferences
    ): BleTransport {
        val encryptionType = when {
            config.isEnabled(ExternalOptions.EMULATE_DANA_RS_V1) -> EncryptionType.ENCRYPTION_DEFAULT
            config.isEnabled(ExternalOptions.EMULATE_DANA_RS_V3) -> EncryptionType.ENCRYPTION_RSv3
            config.isEnabled(ExternalOptions.EMULATE_DANA_BLE5) -> EncryptionType.ENCRYPTION_BLE5
            else                     -> null
        }
        return if (encryptionType != null) {
            EmulatorBleTransport(
                encryptionType = encryptionType,
                pumpDisplay = NotificationPumpDisplay(notificationManager),
                aapsLogger = aapsLogger,
                deviceNameProvider = {
                    var name = preferences.get(DanaStringKey.EmulatorDeviceName)
                    if (name.isEmpty()) {
                        name = "UHH${String.format("%05d", (0..99999).random())}TI"
                        preferences.put(DanaStringKey.EmulatorDeviceName, name)
                    }
                    name
                }
            )
        } else {
            bleTransportImpl
        }
    }
}

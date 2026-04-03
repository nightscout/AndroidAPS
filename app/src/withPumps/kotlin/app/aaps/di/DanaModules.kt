package app.aaps.di

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.rfcomm.RfcommTransport
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.dana.di.DanaHistoryModule
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danar.di.DanaRModule
import app.aaps.pump.danar.emulator.DanaRPumpEmulator
import app.aaps.pump.danar.emulator.DanaRPumpState
import app.aaps.pump.danar.emulator.DanaRVariant
import app.aaps.pump.danar.emulator.EmulatorRfcommTransport
import app.aaps.pump.danar.services.RealRfcommTransport
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.pump.danars.di.DanaRSModule
import app.aaps.pump.danars.emulator.EmulatorBleTransport
import app.aaps.pump.danars.emulator.NotificationPumpDisplay
import app.aaps.pump.danars.encryption.EncryptionType
import app.aaps.pump.danars.services.BleTransportImpl
import app.aaps.pump.danarv2.DanaRv2Plugin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module(
    includes = [
        DanaHistoryModule::class,
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
            config.isEnabled(ExternalOptions.EMULATE_DANA_BLE5)  -> EncryptionType.ENCRYPTION_BLE5
            else                                                 -> null
        }
        return if (encryptionType != null) {
            EmulatorBleTransport(
                encryptionType = encryptionType,
                pumpDisplay = NotificationPumpDisplay(notificationManager),
                aapsLogger = aapsLogger,
                deviceNameProvider = {
                    var name = preferences.get(DanaStringNonKey.EmulatorDeviceName)
                    if (name.isEmpty()) {
                        name = "UHH${String.format("%05d", (0..99999).random())}TI"
                        preferences.put(DanaStringNonKey.EmulatorDeviceName, name)
                    }
                    name
                }
            )
        } else {
            bleTransportImpl
        }
    }

    @Provides
    @Singleton
    fun provideRfcommTransport(
        config: Config,
        realRfcommTransport: RealRfcommTransport,
        aapsLogger: AAPSLogger,
        preferences: Preferences,
        configBuilder: ConfigBuilder,
        danaRPlugin: DanaRPlugin,
        danaRKoreanPlugin: DanaRKoreanPlugin,
        danaRv2Plugin: DanaRv2Plugin
    ): RfcommTransport {
        val variant = when {
            config.isEnabled(ExternalOptions.EMULATE_DANA_R)        -> DanaRVariant.DANA_R
            config.isEnabled(ExternalOptions.EMULATE_DANA_R_KOREAN) -> DanaRVariant.DANA_R_KOREAN
            config.isEnabled(ExternalOptions.EMULATE_DANA_R_V2)     -> DanaRVariant.DANA_R_V2
            else                                                    -> null
        }
        return if (variant != null) {
            // Auto-enable the matching plugin for the emulator variant.
            // The auto-switch in MsgInitConnStatusTime doesn't reliably change the active plugin
            // at runtime, so we ensure the correct plugin is active from the start.
            val targetPlugin = when (variant) {
                DanaRVariant.DANA_R        -> danaRPlugin
                DanaRVariant.DANA_R_KOREAN -> danaRKoreanPlugin
                DanaRVariant.DANA_R_V2     -> danaRv2Plugin
            }
            if (!targetPlugin.isEnabled()) {
                aapsLogger.debug(LTag.PUMP, "Emulator: auto-enabling ${targetPlugin.javaClass.simpleName} for variant $variant")
                for (plugin in listOf(danaRPlugin, danaRKoreanPlugin, danaRv2Plugin)) {
                    plugin.setPluginEnabled(PluginType.PUMP, plugin == targetPlugin)
                    plugin.setFragmentVisible(PluginType.PUMP, plugin == targetPlugin)
                }
                configBuilder.storeSettings("EmulatorVariantAutoSwitch")
            }

            var name = preferences.get(DanaStringNonKey.EmulatorDeviceName)
            if (name.isEmpty()) {
                name = "DAN${String.format("%05d", (0..99999).random())}EM"
                preferences.put(DanaStringNonKey.EmulatorDeviceName, name)
            }
            EmulatorRfcommTransport(
                emulator = DanaRPumpEmulator(
                    state = DanaRPumpState(variant).apply { serialNumber = name },
                    aapsLogger = aapsLogger
                ),
                aapsLogger = aapsLogger,
                deviceName = name
            )
        } else {
            realRfcommTransport
        }
    }
}

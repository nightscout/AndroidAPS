package app.aaps.pump.danars.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danars.emulator.EmulatorBleTransport
import app.aaps.pump.danars.emulator.NotificationPumpDisplay
import app.aaps.pump.danars.encryption.EncryptionType
import app.aaps.pump.danars.services.BleTransportImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Picks the Dana RS transport: the in-tree emulator when one of the Dana RS emulator options is on,
 * otherwise the real BLE one. The option also picks the encryption the emulated pump uses.
 *
 * Lives in the driver rather than in the app's `withPumps` source set, where it used to be. It can,
 * because `:pump:danars:emulator` sits on `:pump:dana` rather than on the driver, so the driver is free
 * to depend on the emulator without a cycle. The app now names no Dana RS type at all.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object DanaRSTransportBindings {

    @Provides
    @SingleIn(AppScope::class)
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
}

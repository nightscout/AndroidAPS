package app.aaps.di.pump

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.carelevo.ble.CarelevoBleTransport
import app.aaps.pump.carelevo.ble.CarelevoBleTransportImpl
import app.aaps.pump.carelevo.emulator.CarelevoEmulatorBleTransport
import app.aaps.pump.carelevo.emulator.CarelevoPumpEmulator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Picks the CareLevo transport: the in-tree emulator when the `emulate_carelevo` marker file is
 * present, otherwise the real BLE one. That way the driver can be exercised end-to-end without
 * patch hardware.
 *
 * `CarelevoBleTransportImpl` carries `@SingleIn(AppScope::class)` but no `@ContributesBinding` - the
 * binding for `CarelevoBleTransport` has to be this function, or the emulator branch would be
 * bypassed.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object CarelevoTransportBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideCarelevoBleTransport(
        config: Config,
        carelevoBleTransportImpl: CarelevoBleTransportImpl,
        aapsLogger: AAPSLogger
    ): CarelevoBleTransport =
        if (config.isEnabled(ExternalOptions.EMULATE_CARELEVO)) {
            aapsLogger.debug(LTag.PUMPEMULATOR, "CareLevo emulator active - real Bluetooth is not used")
            val emulator = CarelevoPumpEmulator(aapsLogger = aapsLogger)
            // Debug-only alarm scenarios, each its own marker file next to `emulate_carelevo` itself —
            // lets an alarm be reproduced on demand (via the emulation-only "re-check alarm snapshot"
            // button in CarelevoOverviewScreen) without real hardware. Critical and advisory are kept
            // independent because the same condition resolves to a different severity on each: critical
            // resolves to a WARNING cause (auto-discards the patch), advisory to the ALERT cause of the
            // same condition (a plain user-clearable alarm) — see CarelevoPumpState.activeAlarmFlags.
            emulator.state.criticalAlarmFlags = emulator.state.criticalAlarmFlags.copy(
                lowBattery = config.isEnabled(ExternalOptions.EMULATE_CARELEVO_LOW_BATTERY),
                occlusionDetected = config.isEnabled(ExternalOptions.EMULATE_CARELEVO_OCCLUSION)
            )
            emulator.state.advisoryAlarmFlags = emulator.state.advisoryAlarmFlags.copy(
                lowBattery = config.isEnabled(ExternalOptions.EMULATE_CARELEVO_LOW_BATTERY_ALERT),
                outOfRangeTemperature = config.isEnabled(ExternalOptions.EMULATE_CARELEVO_INVALID_TEMPERATURE)
            )
            CarelevoEmulatorBleTransport(
                emulator = emulator,
                aapsLogger = aapsLogger
            )
        } else {
            carelevoBleTransportImpl
        }
}

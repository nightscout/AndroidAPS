package app.aaps.plugins.eversense.di

import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.ble.EmulatedBleCentral
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.plugins.eversense.EversensePlugin
import app.aaps.plugins.eversense.emulator.EversenseE3Peripheral
import app.aaps.plugins.eversense.emulator.EversenseTransmitterEmulator
import app.aaps.plugins.eversense.pairing.EversensePairing
import app.aaps.plugins.eversense.pairing.EversensePairingImpl
import app.aaps.plugins.eversense.pairing.EversensePairingViewModel
import app.aaps.plugins.eversense.placement.EversensePlacementViewModel
import app.aaps.plugins.eversense.storage.EversenseStateStore
import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.eversense.ui.EversenseStatusViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metro.IntKey as MetroIntKey

/**
 * What makes Eversense appear in the app: the BG source plugin, its pairing and store, and the ViewModels of its
 * screens.
 *
 * It is not contributed by itself. A platform with Bluetooth for the driver includes it (Android:
 * `EversenseAndroidRegistration`, iOS: `EversenseIosRegistration`) and adds the [app.aaps.plugins.eversense.storage.EversenseLegacyStore]
 * of that platform. The desktop has no transmitter, so it has no Eversense at all rather than one that cannot work.
 */
@BindingContainer
object EversenseRegistration {

    @Provides
    @IntoMap
    @MetroIntKey(415)
    fun plugin(plugin: EversensePlugin): PluginBase = plugin

    /**
     * The phone's Bluetooth, or an emulated E3 when the `emulate_eversense` option file is there, so the driver can be
     * run end to end with no transmitter. The emulated transmitter lives in [PumpEmulators]: an app restart in a test
     * finds the same one, with its clock and calibrations.
     */
    @Provides
    @SingleIn(AppScope::class)
    @EversenseCentral
    fun central(config: Config, phone: BleCentral, aapsLogger: AAPSLogger, emulators: PumpEmulators): BleCentral =
        if (config.isEnabled(ExternalOptions.EMULATE_EVERSENSE)) {
            aapsLogger.debug(LTag.BGSOURCE, "Eversense emulator active - real Bluetooth is not used")
            val transmitter = emulators.getOrCreate(EversenseTransmitterEmulator.KEY) { EversenseTransmitterEmulator() }
            EmulatedBleCentral(aapsLogger).also { it.add(EversenseE3Peripheral(transmitter, aapsLogger)) }
        } else phone

    @Provides
    fun pairing(pairing: EversensePairingImpl): EversensePairing = pairing

    @Provides
    fun store(store: EversenseStateStore): EversenseStore = store

    @Provides
    @IntoMap
    @ViewModelKey(EversensePairingViewModel::class)
    fun pairingViewModel(viewModel: EversensePairingViewModel): ViewModel = viewModel

    @Provides
    @IntoMap
    @ViewModelKey(EversenseStatusViewModel::class)
    fun statusViewModel(viewModel: EversenseStatusViewModel): ViewModel = viewModel

    @Provides
    @IntoMap
    @ViewModelKey(EversensePlacementViewModel::class)
    fun placementViewModel(viewModel: EversensePlacementViewModel): ViewModel = viewModel
}

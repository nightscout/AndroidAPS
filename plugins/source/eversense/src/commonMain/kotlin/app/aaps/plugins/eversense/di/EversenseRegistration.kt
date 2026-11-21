package app.aaps.plugins.eversense.di

import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.eversense.EversensePlugin
import app.aaps.plugins.eversense.pairing.EversensePairing
import app.aaps.plugins.eversense.pairing.EversensePairingImpl
import app.aaps.plugins.eversense.pairing.EversensePairingViewModel
import app.aaps.plugins.eversense.placement.EversensePlacementViewModel
import app.aaps.plugins.eversense.storage.EversenseStateStore
import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.eversense.ui.EversenseStatusViewModel
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
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

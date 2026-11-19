package app.aaps.pump.carelevo.di

import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.carelevo.CarelevoPumpPlugin
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoAlarmViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoOverviewViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchNeedleInsertionViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchSafetyCheckViewModel
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metro.IntKey as MetroIntKey

/**
 * What makes Carelevo appear in the app: the pump plugin and the ViewModels of its screens.
 *
 * It is not contributed by itself. A platform includes it once it has implemented `CarelevoAppForeground`,
 * `CarelevoSystemNotifier` and `CarelevoBleReadyCheck` (Android: `CarelevoAndroidRegistration`). A platform without
 * them has no Carelevo at all, so it never offers a pump that cannot work.
 */
@BindingContainer
object CarelevoRegistration {

    // Pump plugin registration — @MetroIntKey range 1000–1200, see AppRootGraph for the overview
    @Provides
    @IntoMap
    @PumpDriver
    @MetroIntKey(1190)
    fun carelevoPumpPlugin(plugin: CarelevoPumpPlugin): PluginBase = plugin

    @Provides
    @IntoMap
    @ViewModelKey(CarelevoOverviewViewModel::class)
    fun overviewViewModel(viewModel: CarelevoOverviewViewModel): ViewModel = viewModel

    @Provides
    @IntoMap
    @ViewModelKey(CarelevoAlarmViewModel::class)
    fun alarmViewModel(viewModel: CarelevoAlarmViewModel): ViewModel = viewModel

    @Provides
    @IntoMap
    @ViewModelKey(CarelevoPatchConnectionFlowViewModel::class)
    fun patchConnectionFlowViewModel(viewModel: CarelevoPatchConnectionFlowViewModel): ViewModel = viewModel

    @Provides
    @IntoMap
    @ViewModelKey(CarelevoPatchConnectViewModel::class)
    fun patchConnectViewModel(viewModel: CarelevoPatchConnectViewModel): ViewModel = viewModel

    @Provides
    @IntoMap
    @ViewModelKey(CarelevoPatchSafetyCheckViewModel::class)
    fun patchSafetyCheckViewModel(viewModel: CarelevoPatchSafetyCheckViewModel): ViewModel = viewModel

    @Provides
    @IntoMap
    @ViewModelKey(CarelevoPatchNeedleInsertionViewModel::class)
    fun patchNeedleInsertionViewModel(viewModel: CarelevoPatchNeedleInsertionViewModel): ViewModel = viewModel
}

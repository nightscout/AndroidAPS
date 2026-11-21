package app.aaps.pump.aaruy.di

import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.aaruy.AaruyPlugin
import app.aaps.pump.aaruy.compose.AaruyHistoryViewModel
import app.aaps.pump.aaruy.compose.AaruyOverviewViewModel
import app.aaps.pump.aaruy.compose.AaruyPairWizardViewModel
import app.aaps.pump.aaruy.compose.AaruyReplaceWizardViewModel
import app.aaps.pump.aaruy.database.AaruyHistoryDatabase
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metro.IntKey as MetroIntKey

/**
 * What makes Aaruy appear in the app: the pump plugin, the ViewModels of its screens and the DAOs of its pump log.
 *
 * It is not contributed by itself (acceptance K2). A platform includes it once it has its ports: the pump log database,
 * `AaruyBleReadyCheck`, `AaruyEmulatorTriggers`, and a bound `PumpAlerts` (Android: `AaruyAndroidRegistration`, iOS:
 * `AaruyIosRegistration`). A platform without them has no Aaruy at all, so it never offers a pump that cannot work.
 */
@BindingContainer
object AaruyRegistration {

    // Pump plugin registration: @MetroIntKey range 1000-1200, see AppRootGraph for the overview
    @Provides
    @IntoMap
    @PumpDriver
    @MetroIntKey(1195)
    fun aaruyPumpPlugin(plugin: AaruyPlugin): PluginBase = plugin

    @Provides
    @IntoMap
    @ViewModelKey(AaruyOverviewViewModel::class)
    fun overviewViewModel(viewModel: AaruyOverviewViewModel): ViewModel = viewModel

    @Provides
    @IntoMap
    @ViewModelKey(AaruyHistoryViewModel::class)
    fun historyViewModel(viewModel: AaruyHistoryViewModel): ViewModel = viewModel

    @Provides
    @IntoMap
    @ViewModelKey(AaruyPairWizardViewModel::class)
    fun pairWizardViewModel(viewModel: AaruyPairWizardViewModel): ViewModel = viewModel

    @Provides
    @IntoMap
    @ViewModelKey(AaruyReplaceWizardViewModel::class)
    fun replaceWizardViewModel(viewModel: AaruyReplaceWizardViewModel): ViewModel = viewModel

    @Provides
    @SingleIn(AppScope::class)
    fun historyDailyUnitInfoDao(database: AaruyHistoryDatabase): HistoryDailyUnitInfoDao = database.historyDailyUnitInfoDao()

    @Provides
    @SingleIn(AppScope::class)
    fun historyUnitInfoDao(database: AaruyHistoryDatabase): HistoryUnitInfoDao = database.historyUnitInfoDao()
}

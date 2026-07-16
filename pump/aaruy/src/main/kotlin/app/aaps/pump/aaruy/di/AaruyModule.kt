package app.aaps.pump.aaruy.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap
import app.aaps.pump.aaruy.services.AaruyService
import app.aaps.pump.aaruy.ui.AaruyActivity
import app.aaps.pump.aaruy.ui.AaruyBLEScanActivity
import app.aaps.pump.aaruy.ui.AaruyHistoryLogActivity
import app.aaps.pump.aaruy.ui.AaruyOverviewFragment
import app.aaps.pump.aaruy.ui.AaruyReservoirBatFragment
import app.aaps.pump.aaruy.ui.viewmodel.AaruyHistoryViewModel
import app.aaps.pump.aaruy.ui.viewmodel.AaruyOverviewViewModel
import app.aaps.pump.aaruy.ui.viewmodel.AaruyViewModel
import app.aaps.pump.aaruy.ui.viewmodel.ViewModelFactory
import app.aaps.pump.aaruy.ui.viewmodel.ViewModelKey
import javax.inject.Provider

@Module(includes = [
    AaruyHistoryModule::class
])
@Suppress("unused")
abstract class AaruyModule {

    companion object {

        @Provides
        @AaruyPluginQualifier
        fun providesViewModelFactory(@AaruyPluginQualifier viewModels: MutableMap<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>): ViewModelProvider.Factory {
            return ViewModelFactory(viewModels)
        }
    }

    // VIEW MODELS
    @Binds
    @IntoMap
    @AaruyPluginQualifier
    @ViewModelKey(AaruyOverviewViewModel::class)
    internal abstract fun bindsAaruyOverviewViewmodel(viewModel: AaruyOverviewViewModel): ViewModel

    @Binds
    @IntoMap
    @AaruyPluginQualifier
    @ViewModelKey(AaruyViewModel::class)
    internal abstract fun bindsAaruyViewModel(viewModel: AaruyViewModel): ViewModel

    @Binds
    @IntoMap
    @AaruyPluginQualifier
    @ViewModelKey(AaruyHistoryViewModel::class)
    internal abstract fun bindsAaruyHistoryViewModel(viewModel: AaruyHistoryViewModel): ViewModel

    // FRAGMENTS
    @ContributesAndroidInjector
    abstract fun contributesAaruyOverviewFragment(): AaruyOverviewFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesReservoirBatFragment(): AaruyReservoirBatFragment

    // ACTIVITIES
    @ContributesAndroidInjector
    abstract fun contributesAaruyActivity(): AaruyActivity

    @ContributesAndroidInjector
    abstract fun contributesAaruyBLEScanActivity(): AaruyBLEScanActivity

    @ContributesAndroidInjector
    abstract fun contributesAaruyHistoryLogScanActivity(): AaruyHistoryLogActivity

    // SERVICE
    @ContributesAndroidInjector
    abstract fun contributesAaruyService(): AaruyService
}
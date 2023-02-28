package info.nightscout.pump.medtrum.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap
// import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.OverviewViewModel
import info.nightscout.pump.medtrum.services.MedtrumService
import info.nightscout.pump.medtrum.ui.MedtrumOverviewFragment
import info.nightscout.pump.medtrum.ui.viewmodel.OverviewViewModel
// import info.nightscout.pump.medtrum.ui.viewmodel.ViewModel
import info.nightscout.pump.medtrum.ui.viewmodel.ViewModelFactory
import info.nightscout.pump.medtrum.ui.viewmodel.ViewModelKey
import javax.inject.Provider


@Module
@Suppress("unused")
abstract class MedtrumModule {
    companion object {

        @Provides
        @MedtrumPluginQualifier
        fun providesViewModelFactory(@MedtrumPluginQualifier viewModels: MutableMap<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>): ViewModelProvider.Factory {
            return ViewModelFactory(viewModels)
        }
    }

    // VIEW MODELS
    @Binds
    @IntoMap
    @MedtrumPluginQualifier
    @ViewModelKey(OverviewViewModel::class)
    internal abstract fun bindsOverviewViewmodel(viewModel: OverviewViewModel): ViewModel

    // FRAGMENTS
    @ContributesAndroidInjector
    abstract fun contributesMedtrumOverviewFragment(): MedtrumOverviewFragment

    // SERVICE
    @ContributesAndroidInjector 
    abstract fun contributesDanaRSService(): MedtrumService
}
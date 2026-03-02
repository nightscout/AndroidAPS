package app.aaps.pump.medtrum.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.ui.compose.ViewModelFactory
import app.aaps.core.ui.compose.ViewModelKey
import app.aaps.pump.medtrum.MedtrumPlugin
import app.aaps.pump.medtrum.compose.MedtrumOverviewViewModel
import app.aaps.pump.medtrum.compose.MedtrumPatchViewModel
import app.aaps.pump.medtrum.services.MedtrumService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Provider

@Module(includes = [MedtrumCommModule::class])
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
    @ViewModelKey(MedtrumOverviewViewModel::class)
    internal abstract fun bindsMedtrumOverviewViewModel(viewModel: MedtrumOverviewViewModel): ViewModel

    @Binds
    @IntoMap
    @MedtrumPluginQualifier
    @ViewModelKey(MedtrumPatchViewModel::class)
    internal abstract fun bindsMedtrumPatchViewModel(viewModel: MedtrumPatchViewModel): ViewModel

    // SERVICE
    @ContributesAndroidInjector
    abstract fun contributesMedtrumService(): MedtrumService

    // Pump plugin registration — @IntKey range 1000–1200, see PluginsListModule for overview
    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1120)
    abstract fun bindMedtrumPlugin(plugin: MedtrumPlugin): PluginBase
}

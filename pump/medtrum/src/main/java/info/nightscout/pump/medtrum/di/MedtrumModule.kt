package info.nightscout.pump.medtrum.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap
import info.nightscout.androidaps.plugins.pump.eopatch.ui.MedtrumPreparePatchFragment
import info.nightscout.androidaps.plugins.pump.eopatch.ui.MedtrumPrimeFragment
import info.nightscout.pump.medtrum.services.MedtrumService
import info.nightscout.pump.medtrum.ui.MedtrumActivity
import info.nightscout.pump.medtrum.ui.MedtrumOverviewFragment
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumOverviewViewModel
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumViewModel
import info.nightscout.pump.medtrum.ui.viewmodel.ViewModelFactory
import info.nightscout.pump.medtrum.ui.viewmodel.ViewModelKey
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
    internal abstract fun bindsMedtrumOverviewViewmodel(viewModel: MedtrumOverviewViewModel): ViewModel

    @Binds
    @IntoMap
    @MedtrumPluginQualifier
    @ViewModelKey(MedtrumViewModel::class)
    internal abstract fun bindsMedtrumViewModel(viewModel: MedtrumViewModel): ViewModel

    // FRAGMENTS
    @ContributesAndroidInjector
    abstract fun contributesMedtrumOverviewFragment(): MedtrumOverviewFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesPreparePatchFragment(): MedtrumPreparePatchFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesPrimeFragment(): MedtrumPrimeFragment

    // ACTIVITIES
    @ContributesAndroidInjector
    abstract fun contributesMedtrumActivity(): MedtrumActivity

    // SERVICE
    @ContributesAndroidInjector
    abstract fun contributesMedtrumService(): MedtrumService
}

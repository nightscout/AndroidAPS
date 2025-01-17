package app.aaps.pump.medtrum.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap
import app.aaps.pump.medtrum.services.MedtrumService
import app.aaps.pump.medtrum.ui.MedtrumActivateCompleteFragment
import app.aaps.pump.medtrum.ui.MedtrumActivateFragment
import app.aaps.pump.medtrum.ui.MedtrumActivity
import app.aaps.pump.medtrum.ui.MedtrumAttachPatchFragment
import app.aaps.pump.medtrum.ui.MedtrumDeactivatePatchFragment
import app.aaps.pump.medtrum.ui.MedtrumDeactivationCompleteFragment
import app.aaps.pump.medtrum.ui.MedtrumOverviewFragment
import app.aaps.pump.medtrum.ui.MedtrumPreparePatchConnectFragment
import app.aaps.pump.medtrum.ui.MedtrumPreparePatchFragment
import app.aaps.pump.medtrum.ui.MedtrumPrimeCompleteFragment
import app.aaps.pump.medtrum.ui.MedtrumPrimeFragment
import app.aaps.pump.medtrum.ui.MedtrumPrimingFragment
import app.aaps.pump.medtrum.ui.MedtrumRetryActivationConnectFragment
import app.aaps.pump.medtrum.ui.MedtrumRetryActivationFragment
import app.aaps.pump.medtrum.ui.MedtrumStartDeactivationFragment
import app.aaps.pump.medtrum.ui.viewmodel.MedtrumOverviewViewModel
import app.aaps.pump.medtrum.ui.viewmodel.MedtrumViewModel
import app.aaps.pump.medtrum.ui.viewmodel.ViewModelFactory
import app.aaps.pump.medtrum.ui.viewmodel.ViewModelKey
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
    internal abstract fun contributesStartDeactivationFragment(): MedtrumStartDeactivationFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesDeactivatePatchFragment(): MedtrumDeactivatePatchFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesDeactivationCompleteFragment(): MedtrumDeactivationCompleteFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesPreparePatchFragment(): MedtrumPreparePatchFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesPreparePatchConnectFragment(): MedtrumPreparePatchConnectFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesRetryActivationFragment(): MedtrumRetryActivationFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesRetryActivationConnectFragment(): MedtrumRetryActivationConnectFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesPrimeFragment(): MedtrumPrimeFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesPrimeCompleteFragment(): MedtrumPrimeCompleteFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesPrimingFragment(): MedtrumPrimingFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesAttachPatchFragment(): MedtrumAttachPatchFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesActivateFragment(): MedtrumActivateFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesActivateCompleteFragment(): MedtrumActivateCompleteFragment

    // ACTIVITIES
    @ContributesAndroidInjector
    abstract fun contributesMedtrumActivity(): MedtrumActivity

    // SERVICE
    @ContributesAndroidInjector
    abstract fun contributesMedtrumService(): MedtrumService

}

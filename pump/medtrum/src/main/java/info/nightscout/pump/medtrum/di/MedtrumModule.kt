package info.nightscout.pump.medtrum.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap
import info.nightscout.pump.medtrum.services.MedtrumService
import info.nightscout.pump.medtrum.ui.MedtrumActivateCompleteFragment
import info.nightscout.pump.medtrum.ui.MedtrumActivateFragment
import info.nightscout.pump.medtrum.ui.MedtrumAttachPatchFragment
import info.nightscout.pump.medtrum.ui.MedtrumDeactivatePatchFragment
import info.nightscout.pump.medtrum.ui.MedtrumDeactivationCompleteFragment
import info.nightscout.pump.medtrum.ui.MedtrumPreparePatchConnectFragment
import info.nightscout.pump.medtrum.ui.MedtrumPreparePatchFragment
import info.nightscout.pump.medtrum.ui.MedtrumPrimeCompleteFragment
import info.nightscout.pump.medtrum.ui.MedtrumPrimeFragment
import info.nightscout.pump.medtrum.ui.MedtrumPrimingFragment
import info.nightscout.pump.medtrum.ui.MedtrumStartDeactivationFragment
import info.nightscout.pump.medtrum.ui.MedtrumActivity
import info.nightscout.pump.medtrum.ui.MedtrumOverviewFragment
import info.nightscout.pump.medtrum.ui.MedtrumRetryActivationConnectFragment
import info.nightscout.pump.medtrum.ui.MedtrumRetryActivationFragment
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

package info.nightscout.androidaps.plugins.pump.omnipod.dagger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.fragment.action.DeactivatePodActionFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.fragment.action.InsertCannulaActionFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.fragment.action.PairAndPrimePodActionFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.fragment.info.*
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.viewmodel.DeactivatePodActionViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.viewmodel.InsertCannulaActionViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.viewmodel.PairAndPrimePodActionViewModel
import javax.inject.Provider

@Module
abstract class OmnipodWizardModule {

    companion object {
        @Provides
        @OmnipodPluginQualifier
        fun providesViewModelFactory(@OmnipodPluginQualifier viewModels: MutableMap<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>): ViewModelProvider.Factory {
            return ViewModelFactory(viewModels)
        }
    }

    // #### VIEW MODELS ############################################################################
    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(PairAndPrimePodActionViewModel::class)
    internal abstract fun pairAndPrimePodActionViewModel(viewModel: PairAndPrimePodActionViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(InsertCannulaActionViewModel::class)
    internal abstract fun insertCannulaActionViewModel(viewModel: InsertCannulaActionViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(DeactivatePodActionViewModel::class)
    internal abstract fun deactivatePodActionViewModel(viewModel: DeactivatePodActionViewModel): ViewModel

    // #### FRAGMENTS ##############################################################################
    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesDeactivatePodActionFragment(): DeactivatePodActionFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesInsertCannulaActionFragment(): InsertCannulaActionFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesPairAndPrimePodActionFragment(): PairAndPrimePodActionFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesAttachPodInfoFragment(): AttachPodInfoFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesDeactivatePodInfoFragment(): DeactivatePodInfoFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesFillPodInfoFragment(): FillPodInfoFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesPodDeactivatedInfoFragment(): PodDeactivatedInfoFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesPodDiscardedInfoFragment(): PodDiscardedInfoFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesPodChangedInfoFragment(): PodChangedInfoFragment
}



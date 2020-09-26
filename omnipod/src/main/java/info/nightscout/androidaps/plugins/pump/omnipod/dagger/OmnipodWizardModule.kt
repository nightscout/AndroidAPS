package info.nightscout.androidaps.plugins.pump.omnipod.dagger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.action.DeactivatePodActionFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.action.InsertCannulaActionFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.action.PairAndPrimePodActionFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.info.AttachPodInfoFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.info.DeactivatePodInfoFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.info.FillPodInfoFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.info.PodDeactivatedInfoFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.info.PodReplacedInfoFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.viewmodel.WizardViewModel1
import javax.inject.Provider

@Module
abstract class OmnipodWizardModule {

    companion object {
        @Provides
        @JvmStatic
        @OmnipodPluginQualifier
        fun providesViewModelFactory(@OmnipodPluginQualifier viewModels: MutableMap<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>): ViewModelProvider.Factory {
            return ViewModelFactory(viewModels)
        }
    }

    // #### VIEW MODELS ############################################################################
    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(WizardViewModel1::class)
    internal abstract fun bindWizardViewModel1(viewModel: WizardViewModel1): ViewModel
    // Add the rest of the view models

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
    internal abstract fun contributesPodReplacedInfoFragment(): PodReplacedInfoFragment
}



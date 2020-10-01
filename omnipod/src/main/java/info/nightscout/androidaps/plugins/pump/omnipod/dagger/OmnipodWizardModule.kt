package info.nightscout.androidaps.plugins.pump.omnipod.dagger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.fragment.AttachPodInfoFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.fragment.FillPodInfoFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.fragment.InitializePodActionFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.fragment.InsertCannulaActionFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.fragment.PodActivatedInfoFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.viewmodel.InitializePodActionViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.viewmodel.InsertCannulaActionViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.deactivation.fragment.DeactivatePodActionFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.deactivation.fragment.DeactivatePodInfoFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.deactivation.fragment.PodDeactivatedInfoFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.deactivation.fragment.PodDiscardedInfoFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.deactivation.viewmodel.DeactivatePodActionViewModel
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
    @ViewModelKey(InitializePodActionViewModel::class)
    internal abstract fun initializePodActionViewModel(viewModel: InitializePodActionViewModel): ViewModel

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
    internal abstract fun contributesInitializePodActionFragment(): InitializePodActionFragment

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
    internal abstract fun contributesPodActivatedInfoFragment(): PodActivatedInfoFragment
}



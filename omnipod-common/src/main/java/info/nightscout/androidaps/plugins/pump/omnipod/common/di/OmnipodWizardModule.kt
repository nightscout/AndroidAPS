package info.nightscout.androidaps.plugins.pump.omnipod.common.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.fragment.action.InitializePodFragment
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.fragment.action.InsertCannulaFragment
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.fragment.info.AttachPodFragment
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.fragment.info.PodActivatedFragment
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.fragment.info.StartPodActivationFragment
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.fragment.action.DeactivatePodFragment
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.fragment.info.PodDeactivatedFragment
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.fragment.info.PodDiscardedFragment
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.fragment.info.StartPodDeactivationFragment
import javax.inject.Provider

@Module
abstract class OmnipodWizardModule {

    companion object {

        @Provides
        @OmnipodPluginQualifier
        fun providesViewModelFactory(
            @OmnipodPluginQualifier
            viewModels: MutableMap<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
        ): ViewModelProvider.Factory {
            return ViewModelFactory(viewModels)
        }
    }

    // #### FRAGMENTS ##############################################################################

    // POD ACTIVATION

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesStartPodActivationFragment(): StartPodActivationFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesInitializeActionFragment(): InitializePodFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesAttachPodFragment(): AttachPodFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesInsertCannulaFragment(): InsertCannulaFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesPodActivatedFragment(): PodActivatedFragment

    // POD DEACTIVATION

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesStartPodDeactivationFragment(): StartPodDeactivationFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesDeactivatePodFragment(): DeactivatePodFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesPodDeactivatedFragment(): PodDeactivatedFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesPodDiscardedFragment(): PodDiscardedFragment

}

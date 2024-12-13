package app.aaps.pump.omnipod.common.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.aaps.pump.omnipod.common.ui.wizard.activation.fragment.action.InitializePodFragment
import app.aaps.pump.omnipod.common.ui.wizard.activation.fragment.action.InsertCannulaFragment
import app.aaps.pump.omnipod.common.ui.wizard.activation.fragment.info.AttachPodFragment
import app.aaps.pump.omnipod.common.ui.wizard.activation.fragment.info.PodActivatedFragment
import app.aaps.pump.omnipod.common.ui.wizard.activation.fragment.info.StartPodActivationFragment
import app.aaps.pump.omnipod.common.ui.wizard.deactivation.fragment.action.DeactivatePodFragment
import app.aaps.pump.omnipod.common.ui.wizard.deactivation.fragment.info.PodDeactivatedFragment
import app.aaps.pump.omnipod.common.ui.wizard.deactivation.fragment.info.PodDiscardedFragment
import app.aaps.pump.omnipod.common.ui.wizard.deactivation.fragment.info.StartPodDeactivationFragment
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import javax.inject.Provider

@Suppress("unused")
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

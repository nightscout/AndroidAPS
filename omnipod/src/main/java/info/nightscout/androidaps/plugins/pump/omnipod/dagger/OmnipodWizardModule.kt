package info.nightscout.androidaps.plugins.pump.omnipod.dagger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.WizardFragment1
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
    internal abstract fun contributesWizardFragment1(): WizardFragment1

    // Add the rest of the fragments
}



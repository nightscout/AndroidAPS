package info.nightscout.androidaps.plugins.pump.omnipod.dagger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.WizardFragment1
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.WizardViewModel1
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
class ViewModelFactory @Inject constructor(
    private val viewModels: MutableMap<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModels[modelClass]?.get() as T
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
@MapKey
internal annotation class ViewModelKey(val value: KClass<out ViewModel>)

@Module
abstract class OmnipodWizardModule {

    @Binds
    abstract fun bindViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory

    // VIEW MODELS
    @Binds
    @IntoMap
    @ViewModelKey(WizardViewModel1::class)
    internal abstract fun bindWizardViewModel1(viewModel: WizardViewModel1): ViewModel

    // Add the rest of the view models

    // FRAGMENTS

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesWizardFragment1(): WizardFragment1

    // Add the rest of the fragments
}
package app.aaps.pump.omnipod.common.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.MapKey
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Scope
import kotlin.reflect.KClass

@Qualifier
annotation class OmnipodPluginQualifier

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@MapKey
annotation class ViewModelKey(val value: KClass<out ViewModel>)

// TODO: These annotations and Factories could be used globally -> move to core or app

@Suppress("UNCHECKED_CAST")
class ViewModelFactory @Inject constructor(
    private val viewModels: MutableMap<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModels[modelClass]?.get() as T
}

@MustBeDocumented
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class ActivityScope

@MustBeDocumented
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class FragmentScope

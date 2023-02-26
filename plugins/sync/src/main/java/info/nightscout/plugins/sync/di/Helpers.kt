package info.nightscout.plugins.sync.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.MapKey
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Qualifier
import kotlin.reflect.KClass

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class BaseUrl

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class ClientId

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class ClientSecret

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class RedirectUrl

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class AuthUrl

@MapKey
@Retention(AnnotationRetention.RUNTIME)
internal annotation class ViewModelKey(val value: KClass<out ViewModel>)

@Suppress("UNCHECKED_CAST")
class ViewModelFactory @Inject constructor(
    private val viewModels: MutableMap<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModels[modelClass]?.get() as T
}
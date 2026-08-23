package app.aaps.core.ui.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.reflect.KClass

/**
 * How a view model wired by Metro is built - the `@HiltViewModel` replacement, 77 sites.
 *
 * A view model is constructed by `ViewModelProvider`, not by us, so injection has to come in through a
 * [ViewModelProvider.Factory]. Hilt supplies one and reaches it through `@AndroidEntryPoint` and
 * `hiltViewModel()`. Metro does not, so [MetroViewModelFactory] below is it.
 *
 * [CreationExtras] is passed through rather than a `SavedStateHandle`, because only some view models
 * want one - eight of them here. A view model without one binds a plain provider; a view model with
 * one binds an assisted factory and pulls the handle out of the extras. Both are one line.
 */
fun interface MetroViewModelCreator {

    fun create(extras: CreationExtras): ViewModel
}

/**
 * How an activity reaches the factory. The `Application` implements this, the same way it implements
 * `MetroMemberInjector` - Android builds the activity, so the activity has to reach out and ask.
 */
interface MetroViewModelFactoryOwner {

    val metroViewModelFactory: MetroViewModelFactory
}

/**
 * Builds view models from a class-keyed map that Metro filled at compile time.
 *
 * Worth noting for the multiplatform goal: `hiltViewModel()` is Android-only and always will be, while
 * `viewModel(factory = ...)` is the Compose Multiplatform way to get a view model. So replacing Hilt
 * here is not only about removing Dagger - it is also the shape shared iOS UI needs.
 */
class MetroViewModelFactory(
    private val creators: Map<KClass<*>, MetroViewModelCreator>
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val creator = creators[modelClass.kotlin]
            ?: error("No Metro binding for view model ${modelClass.name}. Add it to a view model graph.")
        @Suppress("UNCHECKED_CAST")
        return creator.create(extras) as T
    }
}

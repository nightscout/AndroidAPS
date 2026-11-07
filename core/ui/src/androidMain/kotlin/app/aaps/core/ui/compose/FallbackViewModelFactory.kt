package app.aaps.core.ui.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.reflect.KClass

/**
 * Tries [primary] for a view model and falls back to [fallback] for anything it does not provide.
 *
 * An activity that sets its `defaultViewModelProviderFactory` replaces the factory for EVERY view
 * model built through it - including ones the app never writes. AndroidX libraries build their own
 * that way: `BiometricPrompt` constructs an `androidx.biometric.BiometricViewModel` from the host
 * activity's `ViewModelProvider`. `MetroViewModelFactory` knows only what the graph contributes and
 * throws `IllegalArgumentException("Unknown model class ...")` for the rest, so handing it out on
 * its own turns every such library into a crash.
 *
 * That is not hypothetical: it took down the biometric prompt, and with it the Configuration screen
 * and profile editing, for anyone with protection switched on.
 *
 * Wrapping rather than subclassing because `MetroViewModelFactory.create` is `final`.
 */
class FallbackViewModelFactory(
    private val primary: ViewModelProvider.Factory,
    private val fallback: ViewModelProvider.Factory
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
        try {
            primary.create(modelClass, extras)
        } catch (_: IllegalArgumentException) {
            // Only "this factory does not know that class" reaches here. A view model the app does
            // own that fails while being built throws something else and is left to propagate.
            fallback.create(modelClass, extras)
        }
}

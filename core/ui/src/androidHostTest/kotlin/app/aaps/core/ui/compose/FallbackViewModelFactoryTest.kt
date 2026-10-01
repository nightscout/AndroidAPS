package app.aaps.core.ui.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.common.truth.Truth.assertThat
import kotlin.reflect.KClass
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Covers [FallbackViewModelFactory].
 *
 * The case that matters is the one that shipped broken: an activity hands out the Metro factory as
 * its default, an AndroidX library asks that activity for a view model of its own -
 * `BiometricPrompt` wanting a `BiometricViewModel` - and the Metro factory throws
 * `IllegalArgumentException` because the graph never contributed that class. The biometric prompt
 * died with it, and so did every screen behind it.
 */
class FallbackViewModelFactoryTest {

    private class AppViewModel : ViewModel()
    private class LibraryViewModel : ViewModel()

    /** Stands in for the Metro factory: knows its own classes, throws for everything else. */
    private class KnowsOnly(private val known: KClass<out ViewModel>, private val instance: ViewModel) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
            if (modelClass == known) instance as T
            else throw IllegalArgumentException("Unknown model class $modelClass")
    }

    /** Stands in for the platform default factory, which can build a plain view model. */
    private class AlwaysBuilds(private val instance: ViewModel) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = instance as T
    }

    private val appViewModel = AppViewModel()
    private val libraryViewModel = LibraryViewModel()

    private fun sut() = FallbackViewModelFactory(
        primary = KnowsOnly(AppViewModel::class, appViewModel),
        fallback = AlwaysBuilds(libraryViewModel)
    )

    @Test
    fun aViewModelTheAppOwnsStillComesFromTheAppFactory() {
        val built = sut().create(AppViewModel::class, CreationExtras.Empty)

        assertThat(built).isSameInstanceAs(appViewModel)
    }

    /** The regression: an AndroidX view model must be built instead of crashing the screen. */
    @Test
    fun aViewModelTheAppDoesNotOwnFallsBackInsteadOfThrowing() {
        val built = sut().create(LibraryViewModel::class, CreationExtras.Empty)

        assertThat(built).isSameInstanceAs(libraryViewModel)
    }

    /**
     * Only "I do not know that class" is caught. A view model the app DOES own which fails while
     * being built must not be quietly rebuilt by the fallback - that would swap a real failure for a
     * half-built object.
     */
    @Test
    fun aRealFailureBuildingAnOwnedViewModelIsNotSwallowed() {
        val exploding = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
                throw IllegalStateException("dependency missing")
        }
        val sut = FallbackViewModelFactory(primary = exploding, fallback = AlwaysBuilds(libraryViewModel))

        assertThrows<IllegalStateException> { sut.create(AppViewModel::class, CreationExtras.Empty) }
    }
}

package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

/**
 * The factory a screen builds its view model with, when the host wants to say so.
 * Null means "ask the platform" - see [platformMetroViewModelFactory]. On Android nothing has to
 * provide this, because the application can be reached from the composition; iOS has no such
 * ambient object, so its host provides one here.
 */
val LocalMetroViewModelFactory = compositionLocalOf<MetroViewModelFactory?> { null }

/**
 * The factory this platform can find on its own, used when [LocalMetroViewModelFactory] is not set.
 * Android finds it through the `Application`, which is reachable from any composition. That is the
 * lookup this used to do inline, and keeping it as the fallback is what makes moving this to
 * commonMain a no-op for every existing Android screen.
 */
@Composable
expect fun platformMetroViewModelFactory(): MetroViewModelFactory

/**
 * The view model itself needs `@ContributesIntoMap` and `@ViewModelKey` on its class; nothing has to
 * be added to a graph. Asking for one that has neither fails here, when the screen opens.
 */
@Composable
inline fun <reified VM : ViewModel> metroViewModel(): VM =
    viewModel(factory = LocalMetroViewModelFactory.current ?: platformMetroViewModelFactory())

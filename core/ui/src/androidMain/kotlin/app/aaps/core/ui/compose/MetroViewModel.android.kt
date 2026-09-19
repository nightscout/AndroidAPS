package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

/**
 * The `Application` is the factory owner, and it is reachable from any composition - so no Android
 * screen has to provide [LocalMetroViewModelFactory] for `metroViewModel()` to work.
 */
@Composable
actual fun platformMetroViewModelFactory(): MetroViewModelFactory =
    (LocalContext.current.applicationContext as MetroViewModelFactoryOwner).metroViewModelFactory

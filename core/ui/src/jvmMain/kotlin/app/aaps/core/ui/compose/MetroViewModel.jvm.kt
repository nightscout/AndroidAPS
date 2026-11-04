package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

/**
 * Desktop has no ambient application object to read a factory from, so the host must provide
 * [LocalMetroViewModelFactory] around the shared UI. Failing here names the missing wiring, rather
 * than letting a screen fail later with something that reads like a graph problem.
 */
@Composable
actual fun platformMetroViewModelFactory(): MetroViewModelFactory =
    error("No MetroViewModelFactory. Provide LocalMetroViewModelFactory around the shared UI.")

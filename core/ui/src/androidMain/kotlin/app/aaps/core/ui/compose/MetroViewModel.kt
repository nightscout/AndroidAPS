package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Gets a Metro-built view model - the `hiltViewModel()` replacement, and the same length to write.
 *
 * `hiltViewModel()` only ever worked because the host activity was `@AndroidEntryPoint`. Metro has no
 * such hook, so the factory is reached through the `Application`, which implements
 * [MetroViewModelFactoryOwner]. That lookup is the only difference, and it is hidden here rather than
 * repeated at nearly sixty call sites.
 *
 * The view model itself needs `@ContributesIntoMap` and `@ViewModelKey` on its class; nothing has to be
 * added to a graph. Asking for one that has neither fails here, when the screen opens.
 */
@Composable
inline fun <reified VM : ViewModel> metroViewModel(): VM =
    viewModel(
        factory = (LocalContext.current.applicationContext as MetroViewModelFactoryOwner).metroViewModelFactory
    )

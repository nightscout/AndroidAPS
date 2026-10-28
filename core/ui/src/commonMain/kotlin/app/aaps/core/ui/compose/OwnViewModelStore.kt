package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * A view model store of its own, cleared when the caller leaves the composition. Provide it as
 * `LocalViewModelStoreOwner`.
 *
 * For the iOS and desktop shells, which rebuild the app with `key(uiRestart.signal)` after a language
 * change or an import. The key alone did not rebuild the view models: they live in the window's store,
 * outside the key, so the overview came back with the texts it had built in the old language. Asked for
 * inside the key, this store is new for every rebuild.
 *
 * Not for Android: its activity keeps the view models across a rotation, which a store held in the
 * composition would not. `ComposeMainActivity` clears the activity's own store on a rebuild instead.
 */
@Composable
fun rememberOwnViewModelStoreOwner(): ViewModelStoreOwner {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    return owner
}

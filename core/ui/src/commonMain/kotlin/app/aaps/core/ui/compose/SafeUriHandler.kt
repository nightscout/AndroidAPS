package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler

/**
 * [LocalUriHandler] that does not crash when no app can open the link.
 *
 * On Android `openUri` throws `ActivityNotFoundException` when there is no browser, or when one is
 * blocked, for example by Family Link on a child's phone. A tap on a help link must never stop AAPS,
 * so the error is dropped and the tap does nothing - the same as `AndroidUrlOpener` does.
 * The exception type is Android only, so commonMain catches [RuntimeException].
 */
@Composable
fun rememberSafeUriHandler(): UriHandler {
    val handler = LocalUriHandler.current
    return remember(handler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                try {
                    handler.openUri(uri)
                } catch (_: RuntimeException) {
                    // No app can open the link: nothing useful to show, keep running
                }
            }
        }
    }
}

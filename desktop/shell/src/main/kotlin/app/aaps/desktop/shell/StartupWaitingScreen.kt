package app.aaps.desktop.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import app.aaps.core.ui.compose.AapsSpacing
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Shown in the window while the app starts.
 *
 * Usually for a moment only. It matters when opening the settings store waits for the system key store,
 * which may show a dialog of its own and waits for an answer without a time limit. The screen then says
 * what it waits for and where to look.
 *
 * That text comes only after [KEY_STORE_HELP_DELAY], so a normal start does not flash a message about a
 * dialog that never comes. From then on it stays the same, with no spinner, because nobody may be at the
 * computer and the wait can last hours. Nothing here brings the window to the front: macOS does not always
 * put its dialog on top, and a window that jumps up would cover it.
 *
 * English only, like the failure screen: there are no translated strings before the graph exists.
 */
@Composable
internal fun StartupWaitingScreen(openingKeyStore: Boolean) {
    val showKeyStoreHelp by produceState(initialValue = false, openingKeyStore) {
        value = false
        if (openingKeyStore) {
            delay(KEY_STORE_HELP_DELAY)
            value = true
        }
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(AapsSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            if (showKeyStoreHelp) {
                Text("Waiting for the key store", style = MaterialTheme.typography.headlineSmall)
                keyStoreWaitExplanation(System.getProperty("os.name").orEmpty()).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            } else {
                Text("Starting, please wait", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

/**
 * What the key store may be asking, and where to find its dialog. By the same operating system names that
 * [app.aaps.desktop.shell.secure.KeyVaults.osVaultFor] picks the key store by.
 */
internal fun keyStoreWaitExplanation(osName: String): List<String> {
    val os = osName.lowercase()
    return when {
        os.contains("mac") || os.contains("darwin") -> listOf(
            "macOS asks for your login password so that AAPS can read its keys.",
            "If you do not see that dialog, look behind this window.",
            "Choose Always Allow. With Allow, macOS asks again at the next start."
        )

        os.contains("nux") || os.contains("nix")    -> listOf(
            "Your keyring may be locked. It asks for its password so that AAPS can read its keys.",
            "If you do not see that dialog, look behind this window."
        )

        else                                        -> listOf("AAPS is reading the key to your settings from the system key store.")
    }
}

/** How long a start may open the key store before the screen explains the wait. A normal start takes well under this. */
private val KEY_STORE_HELP_DELAY = 2.seconds

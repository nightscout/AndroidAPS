package app.aaps.ios.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.icons.IcAaps
import app.aaps.ios.shell.ShellInfo
import platform.UIKit.UIViewController

/**
 * The first AAPS composables that iOS actually runs.
 *
 * `:core:ui` has compiled for iOS since the first link, but nothing had ever executed it: a
 * composable only runs inside a composition, and until now the app had no composition to put one
 * in. This is that composition, and everything it draws below the framing text is AAPS code from
 * `commonMain` - [AapsCard], [AapsSpacing] and the [IcAaps] vector - not Material defaults.
 *
 * ## Why not [app.aaps.core.ui.compose.AapsTheme] yet
 *
 * `AapsTheme` reads `LocalPreferences` to pick light or dark, so it needs a real `Preferences`.
 * That is one object, but it sits on top of a chain: `PreferencesImpl` wants `PersistenceLayer`,
 * `ProfileUtil`, `ProfileFunction` and `HardLimits`, and each of those wants more again.
 *
 * Most of the chain is already common code and Metro resolves it on iOS today. The way to see what
 * is left is to ask: add `val preferences: Preferences` to `IosProbeGraph` and read the error.
 * At the time of writing it names `ProfileStore`, whose only implementation is still on Dagger and
 * still holds an `androidx.collection.ArrayMap`, so it cannot cross into common code yet.
 *
 * Faking `Preferences` instead would only test the fake - it has 76 members - so this screen stays
 * on `MaterialTheme` until the chain closes. The theme swap itself is then one line here; the work
 * is all in the chain, not in this file.
 */
fun aapsComposeViewController(): UIViewController = ComposeUIViewController {
    MaterialTheme {
        ShellScreen()
    }
}

/**
 * Deliberately plain.
 *
 * The point is not the layout, it is that every AAPS piece on screen was drawn by shared code:
 * if the card has AAPS's elevation and the logo path renders, `commonMain` Compose ran on iOS.
 */
@Composable
private fun ShellScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(AapsSpacing.xxLarge),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.large, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AapsCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(AapsSpacing.extraLarge),
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = IcAaps,
                        contentDescription = null,
                        modifier = Modifier.size(AapsSpacing.chipIconSize),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "AAPS Compose",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${ShellInfo.LINKED_MODULES} modules linked",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = ShellInfo.localTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

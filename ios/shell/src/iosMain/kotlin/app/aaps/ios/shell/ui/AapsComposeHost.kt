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
 * The only implementation is `PreferencesImpl`, whose constructor wants `PersistenceLayer`,
 * `ProfileUtil`, `ProfileFunction` and `HardLimits`, and `PersistenceLayer` has no iOS
 * implementation yet. Faking it would only test the fake, so this screen stays on `MaterialTheme`
 * until that chain closes, and then the theme swap is one line.
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

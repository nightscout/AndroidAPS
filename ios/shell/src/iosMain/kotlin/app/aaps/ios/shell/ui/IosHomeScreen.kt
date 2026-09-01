package app.aaps.ios.shell.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.aaps.appshell.navigation.AppRoute
import app.aaps.core.ui.compose.AapsSpacing

/**
 * A plain list of the screens iOS can currently open.
 *
 * **Scaffolding, and meant to be deleted.** The real home screen is the overview, which is still
 * assembled inline in `ComposeMainActivity` rather than in `appNavGraph`, so there is nothing shared
 * for iOS to show. When the overview moves, this goes and `AppRoute.Main` becomes the start
 * destination.
 *
 * It exists because starting the app directly on the settings screen had two costs that were easy to
 * miss:
 *
 * - **Back did nothing.** Settings was the start destination, so `safePopBackStack` had an empty
 *   stack to pop. The arrow was there and inert, which reads as a broken screen rather than a
 *   missing one.
 * - **Nothing else was reachable.** Every other screen in `appNavGraph` is navigated to from the
 *   overview, so with no overview they could not be opened at all - including on the simulator, where
 *   the point is to find out which ones work.
 *
 * Only routes that need no arguments are listed. The rest are opened from a screen that has the
 * argument to give - a profile from the profile list, a plugin's preferences from the plugin list -
 * and inventing values here would be testing a screen with data no user would ever hand it.
 */
@Composable
fun IosHomeScreen(onOpen: (route: String) -> Unit) {
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = "AAPS on iOS",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(AapsSpacing.large)
            )
            Text(
                text = "Screens that open without arguments. This list is scaffolding until the " +
                    "overview moves to shared code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AapsSpacing.large)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = AapsSpacing.medium))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(DESTINATIONS) { destination ->
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(destination.route) }
                            .padding(AapsSpacing.large)
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Labels are written here rather than resolved from strings, because these are route names for
 * someone testing the port, not user-facing text. The screens themselves carry the real labels.
 */
private data class Destination(val label: String, val route: String)

private val DESTINATIONS = listOf(
    Destination("Settings", AppRoute.Preferences.route),
    Destination("Configuration", AppRoute.Configuration.route),
    Destination("Treatments", AppRoute.Treatments.route),
    Destination("History browser", AppRoute.HistoryBrowser.route),
    Destination("Statistics", AppRoute.Stats.route),
    Destination("Running mode", AppRoute.RunningMode.route),
    Destination("Profile helper", AppRoute.ProfileHelper.route),
    Destination("New profile", AppRoute.ProfileEditorNew.route),
    Destination("Food", AppRoute.FoodManagement.route),
    Destination("Site rotation", AppRoute.SiteRotationManagement.route),
    Destination("Automation", AppRoute.AutomationList.route),
    Destination("Scenes", AppRoute.SceneList.route),
    Destination("Quick launch", AppRoute.QuickLaunchConfig.route),
    Destination("Authorized clients", AppRoute.AuthorizedClients.route),
    Destination("Pair with master", AppRoute.PairWithMaster.route),
    Destination("Setup wizard", AppRoute.SetupWizard.route),
    Destination("Calibration", AppRoute.CalibrationDialog.route),
    Destination("Carbs", AppRoute.CarbsDialog.route),
    Destination("Insulin", AppRoute.InsulinDialog.route),
    Destination("Extended bolus", AppRoute.ExtendedBolusDialog.route),
    Destination("Temporary basal", AppRoute.TempBasalDialog.route),
    Destination("Treatment", AppRoute.TreatmentDialog.route)
)

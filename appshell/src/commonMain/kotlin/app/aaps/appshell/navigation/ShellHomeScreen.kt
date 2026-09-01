package app.aaps.appshell.navigation

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
import app.aaps.core.ui.compose.AapsSpacing

/**
 * A start destination for the shells that have no overview yet.
 *
 * ## Why it exists
 *
 * The iOS and desktop shells both opened straight onto the settings screen, and that broke the back
 * arrow: settings was the **start** destination, so there was nothing behind it to go back to. It
 * also left every other screen unreachable, because they are all navigated to from the overview,
 * which is still assembled inline in `ComposeMainActivity` rather than in [appNavGraph].
 *
 * A start destination that is a plain list fixes both at once: back works everywhere because there
 * is always something underneath, and each screen can actually be opened and looked at.
 *
 * ## Scaffolding, deliberately
 *
 * This is not a home screen anyone should ship. It is here so the shared screens can be exercised on
 * a platform that has no overview, and it should be **deleted** when the overview moves into
 * `appNavGraph` - at that point the overview becomes the start destination and back works because
 * the app has a real root.
 *
 * The text is in English and not translated for the same reason. Routing it through the string
 * resources would make it look like a finished screen.
 *
 * It lives in `:appshell` rather than in one shell because both need it, and the list of routes is
 * exactly the sort of thing that silently stops matching when it is copied.
 *
 * @param onOpen navigates to the chosen route
 */
@Composable
fun ShellHomeScreen(onOpen: (route: String) -> Unit) {
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = "AAPS",
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
 * The route this screen is registered under.
 *
 * Not an [AppRoute]: those are the app's own screens, and this is scaffolding that goes away.
 */
const val SHELL_HOME_ROUTE: String = "shell_home"

private data class Destination(val label: String, val route: String)

/**
 * Every screen in [appNavGraph] that opens without arguments.
 *
 * A screen needing an argument - editing a specific profile, say - is left out rather than opened
 * with an invented one.
 */
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

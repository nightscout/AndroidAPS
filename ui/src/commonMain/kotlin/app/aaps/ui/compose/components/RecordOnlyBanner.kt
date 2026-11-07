package app.aaps.ui.compose.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.banner.WarningBanner
import app.aaps.core.ui.compose.stringResource

/**
 * The "bolus will be recorded only" warning of the insulin, treatment and wizard dialogs.
 *
 * Put it in the `topBar` slot of the dialog's Scaffold, under the app bar, and not in the scrolling
 * content. Then no scroll can move it off the screen: not the user's, and not the one a section
 * makes when it opens and brings itself into view.
 */
@Composable
fun RecordOnlyBanner() {
    WarningBanner(
        message = stringResource(InterfacesStrings.bolus_recorded_only),
        modifier = Modifier
            .padding(horizontal = AapsSpacing.extraLarge)
            .padding(top = AapsSpacing.medium)
    )
}

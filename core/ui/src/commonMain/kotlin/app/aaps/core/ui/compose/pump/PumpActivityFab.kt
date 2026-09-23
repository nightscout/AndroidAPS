package app.aaps.core.ui.compose.pump

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSmallFab
import app.aaps.core.ui.compose.stringResource

/**
 * FAB indicating active pump communication.
 *
 * Visible when pump is communicating or bolus is in progress.
 * Shows pump icon normally, or delivery percentage during SMB.
 * Tap opens the PumpActivityDialog.
 *
 * @see PreviewPumpFabIcon
 * @see PreviewPumpFabSmbPercent
 */
@Composable
fun PumpActivityFab(
    visible: Boolean,
    bolusState: BolusProgressState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        // Name the Fab itself, not its content. It has two branches - a percent Text and an icon -
        // and only one of them could ever carry a description, so a screen reader announced either
        // a bare "42%" or nothing at all. Naming the Fab covers both: a description on a merging
        // node is added beside the children rather than replacing them, so the percent branch
        // reads "Pump, 42%" and the icon branch simply "Pump".
        val fabDescription = stringResource(CoreUiStrings.pump)
        AapsSmallFab(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = fabDescription }
        ) {
            if (bolusState != null && bolusState.isSMB && bolusState.percent in 1..99) {
                Text(
                    text = "${bolusState.percent}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Vaccines,
                    contentDescription = null
                )
            }
        }
    }
}

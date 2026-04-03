package app.aaps.core.ui.compose.siteRotation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.TE
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert

/**
 * Compact summary widget for embedding in Fill/Care dialogs.
 * Shows "Last site: XXX" and a "Pick site" button.
 * After selection: "Selected: XXX ✓".
 *
 * @param siteType CANNULA_CHANGE or SENSOR_CHANGE — determines icon shown
 * @param lastLocationString formatted string of last used location (or null if none)
 * @param selectedLocationString formatted string of currently selected location (or null if not yet picked)
 * @param onPickSiteClick callback to open the full picker screen
 */
@Composable
fun SiteLocationSummary(
    siteType: TE.Type,
    lastLocationString: String?,
    selectedLocationString: String?,
    onPickSiteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = if (siteType == TE.Type.CANNULA_CHANGE) IcCannulaChange else IcCgmInsert
    val hasSelection = selectedLocationString != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AapsSpacing.medium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(AapsSpacing.medium))

            if (hasSelection) {
                Text(
                    text = stringResource(R.string.selected_location, selectedLocationString),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            } else if (lastLocationString != null) {
                Text(
                    text = stringResource(R.string.last_site_location, lastLocationString),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = stringResource(R.string.select_site_location),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedButton(onClick = onPickSiteClick) {
                Text(stringResource(R.string.pick_site))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SiteLocationSummaryNoSelectionPreview() {
    MaterialTheme {
        SiteLocationSummary(
            siteType = TE.Type.CANNULA_CHANGE,
            lastLocationString = "Right Upper Abdomen",
            selectedLocationString = null,
            onPickSiteClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SiteLocationSummaryWithSelectionPreview() {
    MaterialTheme {
        SiteLocationSummary(
            siteType = TE.Type.SENSOR_CHANGE,
            lastLocationString = "Right Upper Abdomen",
            selectedLocationString = "Left Upper Arm",
            onPickSiteClick = {}
        )
    }
}

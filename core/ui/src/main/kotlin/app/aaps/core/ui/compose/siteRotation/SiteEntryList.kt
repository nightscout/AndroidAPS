package app.aaps.core.ui.compose.siteRotation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert

/**
 * Pre-formatted display data for a site entry row.
 * Decouples the UI from DateUtil/Translator for testability and previews.
 */
@Immutable
data class SiteEntryDisplayData(
    val typeIcon: ImageVector,
    val dateString: String,
    val locationString: String,
    val arrowIcon: ImageVector,
    val note: String?,
    val timestamp: Long,
    val location: TE.Location
)

/**
 * List of site entries with optional edit button and expandable inline editing.
 *
 * @param editingTimestamp when non-null, the entry with this timestamp shows [editingContent] below it
 * @param editingContent composable slot shown below the expanded entry
 */
@Composable
fun SiteEntryList(
    entries: List<SiteEntryDisplayData>,
    showEditButton: Boolean,
    onEntryClick: (SiteEntryDisplayData) -> Unit,
    onEditClick: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
    editingTimestamp: Long? = null,
    editingContent: @Composable ((SiteEntryDisplayData) -> Unit)? = null
) {
    LazyColumn(
        modifier = modifier
    ) {
        items(entries) { entry ->
            SiteEntryRow(
                entry = entry,
                showEditButton = showEditButton,
                onEntryClick = { onEntryClick(entry) },
                onEditClick = { onEditClick?.invoke(entry.timestamp) }
            )
            if (editingContent != null) {
                AnimatedVisibility(
                    visible = editingTimestamp == entry.timestamp,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    editingContent(entry)
                }
            }
        }
    }
}

@Composable
private fun SiteEntryRow(
    entry: SiteEntryDisplayData,
    showEditButton: Boolean,
    onEntryClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasNote = !entry.note.isNullOrBlank()
    val verticalPadding = if (hasNote) AapsSpacing.medium else 0.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEntryClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AapsSpacing.large, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = entry.typeIcon,
                contentDescription = null,
                tint = AapsTheme.elementColors.tempBasal,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(AapsSpacing.medium))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.dateString,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = AapsSpacing.medium)
                    )
                    Text(
                        text = entry.locationString,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (hasNote) {
                    Text(
                        text = entry.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AapsSpacing.extraSmall)
                    )
                }
            }

            Icon(
                imageVector = entry.arrowIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )

            if (showEditButton) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_site),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge),
            thickness = 0.6.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    }
}

/** Convert a TE to display data using DateUtil and Translator. */
fun TE.toDisplayData(
    dateUtil: DateUtil,
    translator: Translator
): SiteEntryDisplayData = SiteEntryDisplayData(
    typeIcon = if (type == TE.Type.CANNULA_CHANGE) IcCannulaChange else IcCgmInsert,
    dateString = dateUtil.dateStringShort(timestamp),
    locationString = translator.translate(location ?: TE.Location.NONE),
    arrowIcon = (arrow ?: TE.Arrow.NONE).directionToComposeIcon(),
    note = note,
    timestamp = timestamp,
    location = location ?: TE.Location.NONE
)

@Preview(showBackground = true)
@Composable
private fun SiteEntryListPreview() {
    MaterialTheme {
        SiteEntryList(
            entries = listOf(
                SiteEntryDisplayData(
                    typeIcon = IcCannulaChange,
                    dateString = "10/03/2026",
                    locationString = "Left Abdomen",
                    arrowIcon = TE.Arrow.UP.directionToComposeIcon(),
                    note = "Rotated clockwise",
                    timestamp = 1741600000000L,
                    location = TE.Location.FRONT_LEFT_UPPER_ABDOMEN
                ),
                SiteEntryDisplayData(
                    typeIcon = IcCgmInsert,
                    dateString = "08/03/2026",
                    locationString = "Right Arm",
                    arrowIcon = TE.Arrow.NONE.directionToComposeIcon(),
                    note = null,
                    timestamp = 1741400000000L,
                    location = TE.Location.SIDE_RIGHT_UPPER_ARM
                )
            ),
            showEditButton = true,
            onEntryClick = {},
            onEditClick = {}
        )
    }
}

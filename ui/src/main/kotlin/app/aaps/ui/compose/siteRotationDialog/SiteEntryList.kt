package app.aaps.ui.compose.siteRotationDialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.TE
import app.aaps.core.objects.extensions.directionToComposeIcon
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.ui.R

/**
 * List of site entries with optional edit button
 *
 * @param filtered list of therapy events entries to display
 * @param showEditButton Whether to show the edit button column
 * @param dateUtil DateUtil for formatting dates
 * @param translator Translator for location names
 * @param onEntryClick Callback when a row is clicked (for filtering)
 * @param onEditClick Callback when edit button is clicked (only if showEditButton is true)
 * @param modifier Modifier for the LazyColumn
 */
@Composable
fun SiteEntryList(
    entries: List<TE>,
    showEditButton: Boolean,
    dateUtil: app.aaps.core.interfaces.utils.DateUtil,
    translator: app.aaps.core.interfaces.utils.Translator,
    onEntryClick: (TE) -> Unit,
    onEditClick: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
    ) {
        items(entries) { te ->
            SiteEntryRow(
                te = te,
                showEditButton = showEditButton,
                dateUtil = dateUtil,
                translator = translator,
                onEntryClick = { onEntryClick(te) },
                onEditClick = { onEditClick?.invoke(te.timestamp) }
            )
        }
    }
}

@Composable
fun SiteEntryRow(
    te: TE,
    showEditButton: Boolean,
    dateUtil: app.aaps.core.interfaces.utils.DateUtil,
    translator: app.aaps.core.interfaces.utils.Translator,
    onEntryClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasNote = !te.note.isNullOrBlank()
    val verticalPadding = if (hasNote) 8.dp else 0.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEntryClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (te.type == TE.Type.CANNULA_CHANGE) IcCannulaChange else IcCgmInsert,
                contentDescription = null,
                tint = AapsTheme.elementColors.tempBasal,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateUtil.dateStringShort(te.timestamp),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = translator.translate(te.location ?: TE.Location.NONE),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (hasNote) {
                    Text(
                        text = te.note ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Icon(
                imageVector = te.arrow?.directionToComposeIcon() ?: TE.Arrow.NONE.directionToComposeIcon(),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )

            if (showEditButton) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onEditClick() }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_site),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.6.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    }
}
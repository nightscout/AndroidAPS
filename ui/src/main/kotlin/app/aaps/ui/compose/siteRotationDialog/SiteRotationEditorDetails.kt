package app.aaps.ui.compose.siteRotationDialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.objects.extensions.directionToComposeIcon
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.ui.R
import app.aaps.core.ui.R as CoreUiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteRotationEditorDetails(
    te: TE?,
    dateString: String,
    locationString: String,
    onArrowClick: () -> Unit,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (te == null) return

    var noteText by remember(te.note) { mutableStateOf(te.note ?: "") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = AapsSpacing.medium)
                    )
                    Text(
                        text = locationString,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            IconButton(
                onClick = onArrowClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = (te.arrow ?: TE.Arrow.NONE).directionToComposeIcon(),
                    contentDescription = stringResource(R.string.select_arrow),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        OutlinedTextField(
            value = noteText,
            onValueChange = {
                noteText = it
                onNoteChange(it)
            },
            label = { Text(stringResource(CoreUiR.string.careportal_note)) },
            modifier = Modifier
                .fillMaxWidth(),
            singleLine = true,
            isError = false,
            shape = MaterialTheme.shapes.small
        )
    }
}

@Composable
fun ArrowSelectionDialog(
    onDismiss: () -> Unit,
    onArrowSelected: (TE.Arrow) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_arrow)) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ArrowIcon(TE.Arrow.UP_LEFT, onArrowSelected)
                    ArrowIcon(TE.Arrow.UP, onArrowSelected)
                    ArrowIcon(TE.Arrow.UP_RIGHT, onArrowSelected)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ArrowIcon(TE.Arrow.LEFT, onArrowSelected)
                    ArrowIcon(TE.Arrow.CENTER, onArrowSelected)
                    ArrowIcon(TE.Arrow.RIGHT, onArrowSelected)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ArrowIcon(TE.Arrow.DOWN_LEFT, onArrowSelected)
                    ArrowIcon(TE.Arrow.DOWN, onArrowSelected)
                    ArrowIcon(TE.Arrow.DOWN_RIGHT, onArrowSelected)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    ArrowIcon(TE.Arrow.NONE, onArrowSelected)
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun ArrowIcon(arrow: TE.Arrow, onArrowSelected: (TE.Arrow) -> Unit) {
    IconButton(
        onClick = { onArrowSelected(arrow) },
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = arrow.directionToComposeIcon(),
            contentDescription = null,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ArrowSelectionDialogPreview() {
    MaterialTheme {
        ArrowSelectionDialog(
            onDismiss = {},
            onArrowSelected = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SiteRotationEditorDetailsPreview() {
    MaterialTheme {
        SiteRotationEditorDetails(
            te = TE(
                timestamp = 1741600000000L,
                type = TE.Type.CANNULA_CHANGE,
                glucoseUnit = GlucoseUnit.MGDL,
                location = TE.Location.FRONT_LEFT_UPPER_ABDOMEN,
                arrow = TE.Arrow.UP,
                note = "Test note"
            ),
            dateString = "10/03/2026",
            locationString = "Left Abdomen",
            onArrowClick = {},
            onNoteChange = {}
        )
    }
}

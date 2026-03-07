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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.objects.extensions.directionToComposeIcon
import app.aaps.ui.R
import app.aaps.core.ui.R as CoreUiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteRotationEditorDetails(
    te: TE?,
    onArrowClick: () -> Unit,
    onNoteChange: (String) -> Unit,
    dateUtil: DateUtil,
    translator: Translator,
    modifier: Modifier = Modifier
) {
    if (te == null) return

    var noteText by remember(te.note) { mutableStateOf(te.note ?: "") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
            }

            // Icône de flèche cliquable
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
            label = { stringResource(CoreUiR.string.careportal_note) },
            modifier = Modifier
                .fillMaxWidth(),
            singleLine = false,
            maxLines = 1,
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
                // Ligne du haut
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ArrowIcon(TE.Arrow.UP_LEFT, onArrowSelected)
                    ArrowIcon(TE.Arrow.UP, onArrowSelected)
                    ArrowIcon(TE.Arrow.UP_RIGHT, onArrowSelected)
                }
                // Ligne du milieu
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ArrowIcon(TE.Arrow.LEFT, onArrowSelected)
                    ArrowIcon(TE.Arrow.CENTER, onArrowSelected)
                    ArrowIcon(TE.Arrow.RIGHT, onArrowSelected)
                }
                // Ligne du bas
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ArrowIcon(TE.Arrow.DOWN_LEFT, onArrowSelected)
                    ArrowIcon(TE.Arrow.DOWN, onArrowSelected)
                    ArrowIcon(TE.Arrow.DOWN_RIGHT, onArrowSelected)
                }
                // Ligne pour "None"
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
fun ArrowIcon(arrow: TE.Arrow, onArrowSelected: (TE.Arrow) -> Unit) {
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
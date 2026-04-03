package app.aaps.core.ui.compose.siteRotation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.TE
import app.aaps.core.ui.R

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

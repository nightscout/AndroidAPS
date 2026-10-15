package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.keys.interfaces.TextRef

/** A value that can only be stepped, as the bolus wizard shows its correction. */
@Preview(showBackground = true)
@Composable
internal fun StepButtonsPreview() {
    MaterialTheme {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            Text("Correction: +0.50 U")
            Spacer(Modifier.weight(1f))
            StepButtons(
                label = "Correction",
                value = 0.5,
                onValueChange = {},
                valueRange = -10.0..10.0,
                step = 0.1,
                unitLabel = TextRef.Literal("U"),
                decimalPlaces = 2
            )
        }
    }
}

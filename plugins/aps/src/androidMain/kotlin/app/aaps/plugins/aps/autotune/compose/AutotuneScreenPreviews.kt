package app.aaps.plugins.aps.autotune.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true)
@Composable
internal fun ResultsTablePreview() {
    MaterialTheme {
        Column {
            ResultsTableHeader(isBasal = false)
            ResultsTableRow(ResultRow("ISF", "5.0", "4.8", "-4%"))
            ResultsTableRow(ResultRow("IC", "10.0", "9.5", "-5%"))
            ResultsTableHeader(isBasal = true)
            ResultsTableRow(ResultRow("00:00", "0.800", "0.900", "13%", "2"))
            ResultsTableRow(ResultRow("∑", "19.200", "20.100", "5%", " "))
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun AutotuneButtonPreview() {
    MaterialTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AutotuneButton("Run Autotune", Icons.Filled.PlayArrow, Modifier.weight(1f)) {}
            AutotuneButton("Compare profiles", Icons.AutoMirrored.Filled.CompareArrows, Modifier.weight(1f)) {}
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun InfoRowPreview() {
    MaterialTheme {
        Column {
            InfoRow(label = "Last run :", value = "2026-04-08 10:00", valueClickable = true, onValueClick = {})
            InfoRow(label = "Warning :", value = "Check the results carefully!")
        }
    }
}

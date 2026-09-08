package app.aaps.pump.omnipod.dash.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.AapsSpacing

@Preview(showBackground = true, name = "History Card - Success with details")
@Composable
internal fun PreviewSuccessCard() {
    MaterialTheme {
        HistoryCardContent(
            commandName = "Set Bolus",
            time = "14:32",
            isSuccess = true,
            description = "2.50 U",
            extra = "Total delivered: 48.25 U"
        )
    }
}

@Preview(showBackground = true, name = "History Card - Success simple")
@Composable
internal fun PreviewSuccessSimple() {
    MaterialTheme {
        HistoryCardContent(
            commandName = "Acknowledge Alerts",
            time = "09:15",
            isSuccess = true
        )
    }
}

@Preview(showBackground = true, name = "History Card - Failure")
@Composable
internal fun PreviewFailure() {
    MaterialTheme {
        HistoryCardContent(
            commandName = "Set Temporary Basal",
            time = "11:47",
            isSuccess = false,
            description = "Command not received by the pod"
        )
    }
}

@Preview(showBackground = true, name = "History Card - TBR")
@Composable
internal fun PreviewTbr() {
    MaterialTheme {
        HistoryCardContent(
            commandName = "Set Temporary Basal",
            time = "08:00",
            isSuccess = true,
            description = "1.50 U/h for 60 min"
        )
    }
}

@Preview(showBackground = true, name = "Filter Chips")
@Composable
internal fun PreviewFilterChips() {
    val groups = listOf("All", "Bolus", "Basal", "Prime", "Alarm", "Config")
    MaterialTheme {
        FlowRow(
            modifier = Modifier.padding(AapsSpacing.extraLarge),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            groups.forEachIndexed { index, name ->
                FilterChip(
                    selected = index == 0,
                    onClick = {},
                    label = { Text(name) }
                )
            }
        }
    }
}

package app.aaps.pump.omnipod.eros.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "Eros History - Success")
@Composable
internal fun PreviewSuccess() {
    MaterialTheme {
        ErosHistoryCardContent(
            commandName = "Set Bolus",
            time = "14:32",
            isSuccess = true,
            description = "2.50 U (15 g carbs)"
        )
    }
}

@Preview(showBackground = true, name = "Eros History - TBR")
@Composable
internal fun PreviewTbr() {
    MaterialTheme {
        ErosHistoryCardContent(
            commandName = "Set Temporary Basal",
            time = "08:00",
            isSuccess = true,
            description = "1.50 U/h, 60 min"
        )
    }
}

@Preview(showBackground = true, name = "Eros History - Failure")
@Composable
internal fun PreviewFailure() {
    MaterialTheme {
        ErosHistoryCardContent(
            commandName = "Deactivate Pod",
            time = "11:47",
            isSuccess = false,
            description = "No response from RileyLink"
        )
    }
}

@Preview(showBackground = true, name = "Eros History - Simple")
@Composable
internal fun PreviewSimple() {
    MaterialTheme {
        ErosHistoryCardContent(
            commandName = "Get Pod Status",
            time = "09:15",
            isSuccess = true
        )
    }
}

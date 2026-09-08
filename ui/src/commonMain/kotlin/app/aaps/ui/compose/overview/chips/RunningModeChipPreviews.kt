package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.RM

@Preview(showBackground = true)
@Composable
internal fun RunningModeChipClosedLoopPreview() {
    MaterialTheme {
        RunningModeChip(
            mode = RM.Mode.CLOSED_LOOP,
            text = "Closed Loop",
            progress = 0f
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun RunningModeChipSuspendedPreview() {
    MaterialTheme {
        RunningModeChip(
            mode = RM.Mode.SUSPENDED_BY_USER,
            text = "Suspended (30 min)",
            progress = 0.4f
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun RunningModeChipClosedLoopSmbPreview() {
    MaterialTheme {
        RunningModeChip(
            mode = RM.Mode.CLOSED_LOOP,
            text = "Closed Loop",
            progress = 0f,
            smbEnabled = true
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun RunningModeChipOpenLoopSmbPreview() {
    MaterialTheme {
        RunningModeChip(
            mode = RM.Mode.OPEN_LOOP,
            text = "Open Loop",
            progress = 0f,
            smbEnabled = true
        )
    }
}

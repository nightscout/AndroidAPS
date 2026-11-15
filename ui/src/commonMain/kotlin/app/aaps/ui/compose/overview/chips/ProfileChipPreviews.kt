package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun ProfileChipPreview() {
    MaterialTheme {
        ProfileChip(
            profileName = "Default 5.6",
            isModified = false,
            progress = 0f,
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun ProfileChipModifiedPreview() {
    MaterialTheme {
        ProfileChip(
            profileName = "Default 5.6 *",
            isModified = true,
            progress = 0.6f,
            onClick = {}
        )
    }
}

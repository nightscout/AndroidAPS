package app.aaps.ui.compose.aboutDialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.ui.R

@Preview(showBackground = true)
@Composable
internal fun AboutAlertDialogPreview() {
    MaterialTheme {
        AboutAlertDialog(
            data = AboutDialogData(
                title = "AndroidAPS 3.3.0",
                message = "Build: 3.3.0-dev\nFlavor: full\n\nhttps://androidaps.org",
                icon = R.drawable.splash_logo,
                enabledOptions = listOf(ExternalOptions.ENGINEERING_MODE, ExternalOptions.UNFINISHED_MODE)
            ),
            onDismiss = {}
        )
    }
}

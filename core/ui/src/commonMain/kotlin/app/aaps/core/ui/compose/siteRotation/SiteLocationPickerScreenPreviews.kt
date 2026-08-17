package app.aaps.core.ui.compose.siteRotation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import app.aaps.core.data.model.TE
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun SiteLocationPickerScreenPreview() {
    MaterialTheme {
        SiteLocationPickerScreen(
            siteType = TE.Type.CANNULA_CHANGE,
            bodyType = BodyType.MAN,
            onClose = {},
            onLocationConfirmed = { _, _ -> }
        )
    }
}

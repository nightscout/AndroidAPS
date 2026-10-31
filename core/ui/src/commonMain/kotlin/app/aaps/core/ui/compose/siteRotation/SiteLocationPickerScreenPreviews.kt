package app.aaps.core.ui.compose.siteRotation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.TE

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

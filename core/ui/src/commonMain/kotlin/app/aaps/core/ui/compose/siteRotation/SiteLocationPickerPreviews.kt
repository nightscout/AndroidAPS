package app.aaps.core.ui.compose.siteRotation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.TE

@Preview(showBackground = true)
@Composable
internal fun SiteLocationPickerPreview() {
    MaterialTheme {
        SiteLocationPicker(
            siteType = TE.Type.CANNULA_CHANGE,
            bodyType = BodyType.MAN,
            entries = emptyList(),
            selectedLocation = TE.Location.FRONT_RIGHT_UPPER_ABDOMEN,
            selectedArrow = TE.Arrow.UP,
            onLocationSelected = {},
            onArrowSelected = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun SiteLocationPickerWithFiltersPreview() {
    MaterialTheme {
        SiteLocationPickerWithFilters(
            bodyType = BodyType.MAN,
            entries = emptyList(),
            showPumpSites = true,
            showCgmSites = true,
            selectedLocation = TE.Location.NONE,
            onLocationSelected = {},
            onShowPumpSites = {},
            onShowCgmSites = {}
        )
    }
}

package app.aaps.core.ui.compose.siteRotation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.TE

@Preview(showBackground = true)
@Composable
internal fun SiteLocationSummaryNoSelectionPreview() {
    MaterialTheme {
        SiteLocationSummary(
            siteType = TE.Type.CANNULA_CHANGE,
            lastLocationString = "Right Upper Abdomen",
            selectedLocationString = null,
            onPickSiteClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun SiteLocationSummaryWithSelectionPreview() {
    MaterialTheme {
        SiteLocationSummary(
            siteType = TE.Type.SENSOR_CHANGE,
            lastLocationString = "Right Upper Abdomen",
            selectedLocationString = "Left Upper Arm",
            onPickSiteClick = {}
        )
    }
}

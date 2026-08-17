package app.aaps.core.ui.compose.siteRotation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.TE
import kotlinx.coroutines.flow.MutableStateFlow

@Preview(showBackground = true)
@Composable
internal fun SiteLocationWizardStepNoSelectionPreview() {
    val host = object : SiteLocationStepHost {
        override val siteLocation = MutableStateFlow(TE.Location.NONE)
        override val siteArrow = MutableStateFlow(TE.Arrow.NONE)
        override fun updateSiteLocation(location: TE.Location) {}
        override fun updateSiteArrow(arrow: TE.Arrow) {}
        override fun completeSiteLocation() {}
        override fun skipSiteLocation() {}
        override fun bodyType() = BodyType.MAN
        override fun siteRotationEntries() = emptyList<TE>()
    }
    MaterialTheme {
        SiteLocationWizardStep(host = host)
    }
}

@Preview(showBackground = true)
@Composable
internal fun SiteLocationWizardStepWithSelectionPreview() {
    val host = object : SiteLocationStepHost {
        override val siteLocation = MutableStateFlow(TE.Location.FRONT_RIGHT_UPPER_ABDOMEN)
        override val siteArrow = MutableStateFlow(TE.Arrow.UP)
        override fun updateSiteLocation(location: TE.Location) {}
        override fun updateSiteArrow(arrow: TE.Arrow) {}
        override fun completeSiteLocation() {}
        override fun skipSiteLocation() {}
        override fun bodyType() = BodyType.MAN
        override fun siteRotationEntries() = emptyList<TE>()
    }
    MaterialTheme {
        SiteLocationWizardStep(host = host)
    }
}

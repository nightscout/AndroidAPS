package app.aaps.core.ui.compose.siteRotation

import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.UiStrings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.TE
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for ViewModels that host the site location wizard step.
 * Implemented by pump-specific ViewModels (Equil, Medtrum, etc.).
 */
interface SiteLocationStepHost {

    val siteLocation: StateFlow<TE.Location>
    val siteArrow: StateFlow<TE.Arrow>
    fun updateSiteLocation(location: TE.Location)
    fun updateSiteArrow(arrow: TE.Arrow)
    fun completeSiteLocation()
    fun skipSiteLocation()
    fun bodyType(): BodyType
    fun siteRotationEntries(): List<TE>
}

/**
 * Shared wizard step for picking a site location during pump activation.
 * Shows a body diagram with Next (enabled when location picked) and Skip buttons.
 *
 * @see SiteLocationWizardStepNoSelectionPreview
 * @see SiteLocationWizardStepWithSelectionPreview
 */
@Composable
fun SiteLocationWizardStep(host: SiteLocationStepHost) {
    val siteLocation by host.siteLocation.collectAsStateWithLifecycle()
    val siteArrow by host.siteArrow.collectAsStateWithLifecycle()

    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(UiStrings.next),
            onClick = { host.completeSiteLocation() },
            enabled = siteLocation != TE.Location.NONE
        ),
        secondaryButton = WizardButton(
            text = stringResource(UiStrings.skip),
            onClick = {
                host.updateSiteLocation(TE.Location.NONE)
                host.updateSiteArrow(TE.Arrow.NONE)
                host.skipSiteLocation()
            }
        ),
        scrollable = false
    ) {
        SiteLocationPicker(
            siteType = TE.Type.CANNULA_CHANGE,
            bodyType = host.bodyType(),
            entries = host.siteRotationEntries(),
            selectedLocation = siteLocation,
            selectedArrow = siteArrow,
            onLocationSelected = { host.updateSiteLocation(it) },
            onArrowSelected = { host.updateSiteArrow(it) }
        )
    }
}

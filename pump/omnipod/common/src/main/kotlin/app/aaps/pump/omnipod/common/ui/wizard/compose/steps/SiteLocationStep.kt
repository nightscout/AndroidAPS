package app.aaps.pump.omnipod.common.ui.wizard.compose.steps

import androidx.compose.runtime.Composable
import app.aaps.core.ui.compose.siteRotation.SiteLocationWizardStep
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardViewModel

/**
 * Site location selection step — shown when site rotation management is enabled.
 * Wraps the shared [SiteLocationWizardStep] composable from core/ui.
 *
 * The ViewModel implements [SiteLocationStepHost], so it's passed directly.
 */
@Composable
fun SiteLocationStep(viewModel: OmnipodWizardViewModel) {
    SiteLocationWizardStep(host = viewModel)
}

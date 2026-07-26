package app.aaps.pump.equil.compose.steps

import androidx.compose.runtime.Composable
import app.aaps.core.ui.compose.siteRotation.SiteLocationWizardStep
import app.aaps.pump.equil.compose.EquilWizardViewModel

@Composable
internal fun SiteLocationStep(
    viewModel: EquilWizardViewModel,
    onCancel: () -> Unit
) {
    SiteLocationWizardStep(host = viewModel)
}

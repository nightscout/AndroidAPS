package app.aaps.pump.medtrum.compose.steps

import androidx.compose.runtime.Composable
import app.aaps.core.ui.compose.siteRotation.SiteLocationWizardStep
import app.aaps.pump.medtrum.compose.MedtrumPatchViewModel

@Composable
fun SiteLocationStep(
    viewModel: MedtrumPatchViewModel,
    onCancel: () -> Unit
) {
    SiteLocationWizardStep(host = viewModel)
}

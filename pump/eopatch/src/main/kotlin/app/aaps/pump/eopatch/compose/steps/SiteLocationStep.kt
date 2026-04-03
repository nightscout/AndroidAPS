package app.aaps.pump.eopatch.compose.steps

import androidx.compose.runtime.Composable
import app.aaps.core.ui.compose.siteRotation.SiteLocationWizardStep
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel

@Composable
fun SiteLocationStep(viewModel: EopatchPatchViewModel) {
    SiteLocationWizardStep(host = viewModel)
}

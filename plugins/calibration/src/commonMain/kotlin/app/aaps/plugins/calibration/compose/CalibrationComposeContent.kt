package app.aaps.plugins.calibration.compose

import androidx.compose.runtime.Composable
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.metroViewModel

internal class CalibrationComposeContent : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        // onSettings intentionally unused — the calibration plugin has no preferences screen.
        val viewModel: CalibrationViewModel = metroViewModel()
        CalibrationScreen(
            viewModel = viewModel,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack
        )
    }
}

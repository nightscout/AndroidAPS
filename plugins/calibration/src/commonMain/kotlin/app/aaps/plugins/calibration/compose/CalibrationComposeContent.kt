package app.aaps.plugins.calibration.compose

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import kotlin.reflect.KClass

/**
 * The dependencies arrive from the plugin rather than from Hilt.
 *
 * `hiltViewModel()` is Android only, which would keep this file and the view model it builds out of
 * common code. `viewModel { }` is the multiplatform equivalent and keeps what actually matters: the
 * view model still outlives a configuration change, which a plain `remember` would not.
 */
internal class CalibrationComposeContent(
    private val persistenceLayer: PersistenceLayer,
    private val profileUtil: ProfileUtil,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        // onSettings intentionally unused — the calibration plugin has no preferences screen.
        val viewModel: CalibrationViewModel = viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
                    CalibrationViewModel(persistenceLayer, profileUtil, aapsLogger, dateUtil) as T
            }
        )
        CalibrationScreen(
            viewModel = viewModel,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack
        )
    }
}

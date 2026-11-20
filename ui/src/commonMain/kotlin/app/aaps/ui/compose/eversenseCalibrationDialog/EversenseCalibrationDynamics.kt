package app.aaps.ui.compose.eversenseCalibrationDialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.banner.WarningBanner
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Uses the same recent glucose data as the overview. It never gates calibration submission. */
@Inject
class EversenseCalibrationDynamics(
    private val lastBgData: LastBgData,
    private val dateUtil: DateUtil
) {
    fun hasRapidChange(): Boolean {
        val last = lastBgData.lastBg() ?: return false
        val age = dateUtil.now() - last.timestamp
        if (age !in 0..Constants.OLD_BG_MINUTES * 60_000L) return false
        return when (last.trendArrow) {
            TrendArrow.SINGLE_UP, TrendArrow.SINGLE_DOWN,
            TrendArrow.DOUBLE_UP, TrendArrow.DOUBLE_DOWN,
            TrendArrow.TRIPLE_UP, TrendArrow.TRIPLE_DOWN -> true
            else -> false
        }
    }
}

@Composable
fun EversenseCalibrationDynamicsWarning(dynamics: EversenseCalibrationDynamics) {
    var rapid by remember(dynamics) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(dynamics) {
        val job = scope.launch {
            while (isActive) {
                rapid = withContext(Dispatchers.Default) { dynamics.hasRapidChange() }
                delay(5_000)
            }
        }
        onPauseOrDispose { job.cancel() }
    }
    EversenseCalibrationDynamicsWarningContent(rapid)
}

@Composable
internal fun EversenseCalibrationDynamicsWarningContent(rapid: Boolean) {
    if (rapid) WarningBanner(stringResource(UiStrings.eversense_calibration_rapid_change_warning))
}

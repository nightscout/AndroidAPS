package app.aaps.plugins.calibration

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.calibration.AddEntryResult
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.calibration.CalibrationContext
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.compose.icons.IcCalibration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoCalibrationPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CALIBRATION)
        .icon(IcCalibration)
        .setDefault(true)
        .pluginName(R.string.no_calibration_name)
        .shortName(R.string.calibration_shortname)
        .description(R.string.description_no_calibration),
    aapsLogger, rh
), Calibration {

    override suspend fun calibrate(
        data: MutableList<InMemoryGlucoseValue>,
        @Suppress("UNUSED_PARAMETER") context: CalibrationContext
    ): MutableList<InMemoryGlucoseValue> = data

    override suspend fun addEntry(bgMgdl: Double, timestamp: Long): AddEntryResult = AddEntryResult.Accepted

    override suspend fun checkPreconditions(): AddEntryResult = AddEntryResult.Accepted
}

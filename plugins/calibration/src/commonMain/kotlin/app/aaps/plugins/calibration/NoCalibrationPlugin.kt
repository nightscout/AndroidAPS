package app.aaps.plugins.calibration

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.calibration.AddEntryResult
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.calibration.CalibrationContext
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.ui.compose.icons.IcCalibration
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(AppScope::class)
// Bound as PluginBase, not implicitly: the class has more than one supertype, so Metro cannot pick
// one. The plugin list wants PluginBase.
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(700)
class NoCalibrationPlugin(
    aapsLogger: AAPSLogger,
    rh: TextResolver
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CALIBRATION)
        .icon(IcCalibration)
        .setDefault(true)
        .pluginName(CalibrationStrings.no_calibration_name)
        .shortName(CalibrationStrings.calibration_shortname)
        .description(CalibrationStrings.description_no_calibration),
    aapsLogger, rh
), Calibration {

    override suspend fun calibrate(
        data: MutableList<InMemoryGlucoseValue>,
        @Suppress("UNUSED_PARAMETER") context: CalibrationContext
    ): MutableList<InMemoryGlucoseValue> = data

    override suspend fun addEntry(bgMgdl: Double, timestamp: Long): AddEntryResult = AddEntryResult.Accepted

    override suspend fun checkPreconditions(): AddEntryResult = AddEntryResult.Accepted
}

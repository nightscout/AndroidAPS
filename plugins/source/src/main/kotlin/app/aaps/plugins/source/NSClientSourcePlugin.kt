package app.aaps.plugins.source

import androidx.annotation.VisibleForTesting
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.NSClientSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NSClientSourcePlugin @Inject constructor(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    config: Config
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_nsclient_bg)
        .pluginName(R.string.ns_client_bg)
        .shortName(R.string.ns_client_bg_short)
        .description(R.string.description_source_ns_client)
        .alwaysEnabled(config.AAPSCLIENT)
        .setDefault(config.AAPSCLIENT),
    aapsLogger, rh
), BgSource, NSClientSource {

    @VisibleForTesting
    var lastBGTimeStamp: Long = 0

    @VisibleForTesting
    var isAdvancedFilteringEnabled = false

    override fun advancedFilteringSupported(): Boolean = isAdvancedFilteringEnabled

    override fun detectSource(glucoseValue: GV) {
        if (glucoseValue.timestamp > lastBGTimeStamp) {
            isAdvancedFilteringEnabled = arrayOf(
                SourceSensor.DEXCOM_NATIVE_UNKNOWN,
                SourceSensor.DEXCOM_G6_NATIVE,
                SourceSensor.DEXCOM_G7_NATIVE,
                SourceSensor.DEXCOM_G6_NATIVE_XDRIP,
                SourceSensor.DEXCOM_G7_NATIVE_XDRIP,
            ).any { it == glucoseValue.sourceSensor }
            lastBGTimeStamp = glucoseValue.timestamp
        }
    }
}
package info.nightscout.androidaps.historyBrowser

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

class IobCobStaticCalculatorPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rxBus: RxBusWrapper,
    sp: SP,
    profileFunction: ProfileFunction,
    configBuilderPlugin: ConfigBuilderPlugin,
    treatmentsPlugin: TreatmentsPlugin
) : IobCobCalculatorPlugin(aapsLogger, rxBus, sp, profileFunction, configBuilderPlugin, treatmentsPlugin) {
    override fun onStart() { // do not attach to rxbus
    }
}
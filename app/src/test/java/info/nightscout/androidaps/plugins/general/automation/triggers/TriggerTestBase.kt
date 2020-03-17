package info.nightscout.androidaps.plugins.general.automation.triggers

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.general.automation.elements.InputBg
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.services.LastLocationDataContainer
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.mockito.Mock
import org.powermock.core.classloader.annotations.PrepareForTest

@PrepareForTest(LastLocationDataContainer::class)
open class TriggerTestBase : TestBase() {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var sp: SP
    @Mock lateinit var locationDataContainer: LastLocationDataContainer
    @Mock lateinit var treatmentsPlugin: TreatmentsPlugin
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin

    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is Trigger) {
                it.aapsLogger = aapsLogger
                it.rxBus = RxBusWrapper()
                it.resourceHelper = resourceHelper
                it.profileFunction = profileFunction
                it.sp = sp
                it.locationDataContainer = locationDataContainer
                it.treatmentsPlugin = treatmentsPlugin
                it.activePlugin = activePlugin
                it.iobCobCalculatorPlugin = iobCobCalculatorPlugin
            }
            if (it is TriggerBg) {
                it.profileFunction = profileFunction
            }
            if (it is InputBg) {
                it.profileFunction = profileFunction
            }
            if (it is GlucoseStatus) {
                it.aapsLogger = aapsLogger
                it.iobCobCalculatorPlugin = iobCobCalculatorPlugin
            }
            if (it is StaticLabel) {
                it.resourceHelper = resourceHelper
            }
        }
    }

}
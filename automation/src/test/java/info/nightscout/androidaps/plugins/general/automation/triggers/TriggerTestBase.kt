package info.nightscout.androidaps.plugins.general.automation.triggers

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.TestPumpPlugin
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.IobCobCalculatorInterface
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin
import info.nightscout.androidaps.plugins.general.automation.elements.InputBg
import info.nightscout.androidaps.plugins.general.automation.elements.InputTempTarget
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.services.LastLocationDataContainer
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Before
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest

@PrepareForTest(LastLocationDataContainer::class, AutomationPlugin::class, AppRepository::class)
open class TriggerTestBase : TestBaseWithProfile() {

    @Mock lateinit var sp: SP
    @Mock lateinit var locationDataContainer: LastLocationDataContainer
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var iobCobCalculatorPlugin: IobCobCalculatorInterface
    @Mock lateinit var context: Context
    @Mock lateinit var automationPlugin: AutomationPlugin
    @Mock lateinit var repository: AppRepository

    lateinit var receiverStatusStore: ReceiverStatusStore
    private val pluginDescription = PluginDescription()
    lateinit var testPumpPlugin : TestPumpPlugin

    @Before
    fun prepareMock1() {
        receiverStatusStore = ReceiverStatusStore(context, rxBus)
        testPumpPlugin = TestPumpPlugin(pluginDescription, aapsLogger, resourceHelper, injector)
        `when`(activePlugin.activePump).thenReturn(testPumpPlugin)
    }

    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is Trigger) {
                it.aapsLogger = aapsLogger
                it.rxBus = RxBusWrapper(aapsSchedulers)
                it.resourceHelper = resourceHelper
                it.profileFunction = profileFunction
                it.sp = sp
                it.locationDataContainer = locationDataContainer
                it.treatmentsInterface = treatmentsInterface
                it.activePlugin = activePlugin
                it.iobCobCalculatorPlugin = iobCobCalculatorPlugin
            }
            if (it is TriggerBg) {
                it.profileFunction = profileFunction
            }
            if (it is TriggerTime) {
                it.dateUtil = dateUtil
            }
            if (it is TriggerTimeRange) {
                it.dateUtil = dateUtil
            }
            if (it is TriggerRecurringTime) {
                it.dateUtil = dateUtil
            }
            if (it is TriggerBTDevice) {
                it.context = context
                it.automationPlugin = automationPlugin
            }
            if (it is TriggerWifiSsid) {
                it.receiverStatusStore = receiverStatusStore
            }
            if (it is InputBg) {
                it.profileFunction = profileFunction
            }
            if (it is InputTempTarget) {
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
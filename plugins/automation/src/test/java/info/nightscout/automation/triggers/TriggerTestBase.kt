package info.nightscout.automation.triggers

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.TestPumpPlugin
import info.nightscout.automation.AutomationPlugin
import info.nightscout.automation.services.LastLocationDataContainer
import info.nightscout.implementation.iob.GlucoseStatusProviderImpl
import info.nightscout.interfaces.aps.AutosensDataStore
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.receivers.ReceiverStatusStore
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.sharedPreferences.SP
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mock
import org.mockito.Mockito.`when`

open class TriggerTestBase : TestBaseWithProfile() {

    @Mock lateinit var sp: SP
    @Mock lateinit var locationDataContainer: LastLocationDataContainer
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var autosensDataStore: AutosensDataStore
    @Mock lateinit var context: Context
    @Mock lateinit var automationPlugin: AutomationPlugin
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    private val pluginDescription = PluginDescription()
    lateinit var testPumpPlugin: TestPumpPlugin

    @BeforeEach
    fun prepareMock1() {
        testPumpPlugin = TestPumpPlugin(pluginDescription, aapsLogger, rh, injector)
        `when`(activePlugin.activePump).thenReturn(testPumpPlugin)
        `when`(iobCobCalculator.ads).thenReturn(autosensDataStore)
    }

    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is Trigger) {
                it.aapsLogger = aapsLogger
                it.rxBus = RxBus(aapsSchedulers, aapsLogger)
                it.rh = rh
                it.profileFunction = profileFunction
                it.sp = sp
                it.locationDataContainer = locationDataContainer
                it.repository = repository
                it.activePlugin = activePlugin
                it.iobCobCalculator = iobCobCalculator
                it.glucoseStatusProvider = GlucoseStatusProviderImpl(aapsLogger, iobCobCalculator, dateUtil)
                it.dateUtil = dateUtil
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
        }
    }

}
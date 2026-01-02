package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.plugins.automation.AutomationPlugin
import app.aaps.plugins.automation.services.LastLocationDataContainer
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mock
import org.mockito.kotlin.whenever

open class TriggerTestBase : TestBaseWithProfile() {

    @Mock lateinit var locationDataContainer: LastLocationDataContainer
    @Mock lateinit var autosensDataStore: AutosensDataStore
    @Mock lateinit var automationPlugin: AutomationPlugin
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var persistenceLayer: PersistenceLayer

    @BeforeEach
    fun prepareMock1() {
        whenever(iobCobCalculator.ads).thenReturn(autosensDataStore)
    }

    init {
        addInjector {
            if (it is Trigger) {
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
                it.rh = rh
                it.profileFunction = profileFunction
                it.preferences = preferences
                it.locationDataContainer = locationDataContainer
                it.persistenceLayer = persistenceLayer
                it.activePlugin = activePlugin
                it.iobCobCalculator = iobCobCalculator
                it.glucoseStatusProvider = smbGlucoseStatusProvider
                it.dateUtil = dateUtil
                it.profileUtil = profileUtil
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
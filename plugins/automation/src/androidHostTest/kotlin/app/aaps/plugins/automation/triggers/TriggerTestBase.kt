package app.aaps.plugins.automation.triggers

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.plugins.automation.BtConnectionSource
import app.aaps.plugins.automation.LastKnownLocation
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mock
import org.mockito.kotlin.whenever

open class TriggerTestBase : TestBaseWithProfile() {

    @Mock lateinit var lastKnownLocation: LastKnownLocation
    @Mock lateinit var autosensDataStore: AutosensDataStore
    @Mock lateinit var btConnectionSource: BtConnectionSource
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var sceneApi: SceneAutomationApi
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var pumpPluginWithConcentration: PumpWithConcentration
    val pumpDescription = PumpDescription()

    @BeforeEach
    fun prepareMock1() {
        whenever(iobCobCalculator.ads).thenReturn(autosensDataStore)
        whenever(activePlugin.activePump).thenReturn(pumpPluginWithConcentration)
        whenever(pumpPluginWithConcentration.pumpDescription).thenReturn(pumpDescription)
        whenever(pumpPluginWithConcentration.lastDataTime).thenReturn(MutableStateFlow(0L))
        whenever(pumpPluginWithConcentration.lastBolusTime).thenReturn(MutableStateFlow(null))
        whenever(pumpPluginWithConcentration.lastBolusAmount).thenReturn(MutableStateFlow(null))
        whenever(pumpPluginWithConcentration.reservoirLevel).thenReturn(MutableStateFlow(PumpInsulin(0.0)))
        whenever(pumpPluginWithConcentration.batteryLevel).thenReturn(MutableStateFlow(null))
    }

    /**
     * Triggers take their dependencies through [TriggerDeps] now, so the long addInjector block that
     * used to wire each one field-by-field is gone. Tests build the trigger under test directly.
     */
    val triggerDeps: TriggerDeps by lazy {
        TriggerDeps(
            aapsLogger, rxBus, rh, profileFunction, profileUtil, preferences, lastKnownLocation,
            persistenceLayer, activePlugin, iobCobCalculator, smbGlucoseStatusProvider, dateUtil
        )
    }

    val triggerFactory: TriggerFactory by lazy {
        TriggerFactory(triggerDeps, { btConnectionSource }, sceneApi, receiverStatusStore)
    }

}
package app.aaps.pump.danarv2

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danarv2.services.DanaRv2ExecutionService
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DanaRv2PluginTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase

    lateinit var danaPump: DanaPump

    private val bolusProgressData by lazy { BolusProgressData(ch, CoroutineScope(Dispatchers.Unconfined)) }

    private lateinit var danaRv2Plugin: DanaRv2Plugin

    @BeforeEach
    fun prepareMocks() {
        whenever(preferences.get(DanaStringNonKey.RName)).thenReturn("")
        whenever(preferences.get(DanaStringNonKey.MacAddress)).thenReturn("")
        whenever(rh.gs(app.aaps.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        whenever(rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        danaPump = DanaPump(aapsLogger, preferences, dateUtil, decimalFormatter, profileStoreProvider)
        danaRv2Plugin = DanaRv2Plugin(
            aapsLogger, rxBus, context, rh, activePlugin, commandQueue, danaPump, detailedBolusInfoStorage,
            temporaryBasalStorage, dateUtil, pumpSync, preferences, config, notificationManager, danaHistoryDatabase, decimalFormatter, bolusProgressData, pumpEnactResultProvider
        )
    }

    /** Binds a mocked service the way Android would. Its bolus() returns [connectionOk] and reports [delivered] U. */
    private fun bindBolusService(connectionOk: Boolean, delivered: Double) {
        runBlocking { danaRv2Plugin.onStart() }
        val connection = argumentCaptor<ServiceConnection>()
        verify(context).bindService(any<Intent>(), connection.capture(), any<Int>())
        val service = mock<DanaRv2ExecutionService>()
        whenever(service.bolus(any())).thenAnswer {
            bolusProgressData.start(1.0, isSMB = false)
            bolusProgressData.updateProgress(delivered = PumpInsulin(delivered))
            connectionOk
        }
        val binder = mock<DanaRv2ExecutionService.LocalBinder>()
        whenever(binder.serviceInstance).thenReturn(service)
        connection.firstValue.onServiceConnected(mock<ComponentName>(), binder)
    }

    @Test
    fun deliveredBolusIsEnacted() {
        bindBolusService(connectionOk = true, delivered = 1.0)
        val result = runBlocking { danaRv2Plugin.deliverTreatment(DetailedBolusInfo().apply { insulin = 1.0 }) }
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
    }

    /** A stopped bolus counts as a success, but if nothing was given nothing changed on the pump. */
    @Test
    fun stoppedBolusWithNothingGivenIsNotEnacted() {
        danaPump.bolusStopped = true
        bindBolusService(connectionOk = true, delivered = 0.0)
        val result = runBlocking { danaRv2Plugin.deliverTreatment(DetailedBolusInfo().apply { insulin = 1.0 }) }
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun failedBolusIsNotEnacted() {
        whenever(rh.gs(eq(app.aaps.pump.dana.R.string.boluserrorcode), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn("bolus error")
        bindBolusService(connectionOk = false, delivered = 0.4)
        val result = runBlocking { danaRv2Plugin.deliverTreatment(DetailedBolusInfo().apply { insulin = 1.0 }) }
        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun basalRateShouldBeLimited() {
        danaRv2Plugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        // cU-domain limit (PumpPluginConstraints); reasons are logged, not surfaced.
        val result = danaRv2Plugin.applyBasalConstraints(PumpRate(Double.MAX_VALUE))
        Assertions.assertEquals(0.8, result.cU, 0.01)
    }

}
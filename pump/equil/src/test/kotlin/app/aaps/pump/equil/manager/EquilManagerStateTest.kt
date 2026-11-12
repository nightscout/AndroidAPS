package app.aaps.pump.equil.manager

import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.ble.EquilBLE
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.driver.definition.ActivationProgress
import app.aaps.pump.equil.driver.definition.BluetoothConnectionState
import app.aaps.pump.equil.keys.EquilStringKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * How the pod state survives a plugin restart.
 *
 * `EquilPumpPlugin.onStart` runs on a background coroutine and calls [EquilManager.init], so it can
 * land at any moment - including while a pod activation is running. Reloading the state there discards
 * whatever is newer in memory, which is what made the former `EquilEmulatorActivationTest` fail two different ways
 * on CI (#5040): once with a stale COMPLETED surviving a reset, once with a finished activation reset
 * back so the wizard waited forever for a COMPLETED it had already reached.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EquilManagerStateTest {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var equilBLE: EquilBLE
    @Mock lateinit var equilHistoryRecordDao: EquilHistoryRecordDao
    @Mock lateinit var equilHistoryPumpDao: EquilHistoryPumpDao
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var ch: ConcentrationHelper
    @Mock lateinit var bolusProgressData: BolusProgressData

    private lateinit var sut: EquilManager

    /** What preferences currently hold, so a write is visible to the next read as on a real device. */
    private var persisted = ""

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.State)).thenAnswer { persisted }
        whenever(preferences.put(org.mockito.kotlin.eq(EquilStringKey.State), org.mockito.kotlin.any())).thenAnswer {
            persisted = it.getArgument(1)
            Unit
        }
        sut = newManager()
    }

    /** A new instance reads only what preferences hold, like a new app process. */
    private fun newManager() = EquilManager(
        aapsLogger, rxBus, preferences, rh, pumpSync, equilBLE, equilHistoryRecordDao,
        equilHistoryPumpDao, { mock<PumpEnactResult>() }, dateUtil,
        ch, bolusProgressData, CoroutineScope(UnconfinedTestDispatcher())
    )

    @Test
    fun `a stored CONNECTING is not loaded after an app restart`() {
        sut.init()
        sut.setActivationProgress(ActivationProgress.COMPLETED)
        // The app is killed while a connect is running; the state was last stored as CONNECTING.
        sut.equilState?.bluetoothConnectionState = BluetoothConnectionState.CONNECTING
        sut.storePodState()

        val restarted = newManager()
        restarted.init()

        // CONNECTING made the queue wait 2 min for a connect that was never started.
        assertThat(restarted.equilState?.bluetoothConnectionState).isEqualTo(BluetoothConnectionState.DISCONNECTED)
        assertThat(restarted.isActivationCompleted()).isTrue()
    }

    /** A stored state with a completed activation, as an earlier manager leaves it behind. */
    private fun storedCompletedPod(): String {
        val earlier = newManager()
        earlier.init()
        earlier.setActivationProgress(ActivationProgress.COMPLETED)
        return persisted
    }

    @Test
    fun `a reset before the first init is not replaced by what is stored`() {
        val completed = storedCompletedPod()
        val manager = newManager()

        manager.clearPodState()
        // Something stale reaches preferences after the reset, then the delayed onStart runs.
        persisted = completed
        manager.init()

        assertThat(manager.isActivationCompleted()).isFalse()
    }

    /**
     * What the former `EquilEmulatorActivationTest` failed with: "activationProgress=COMPLETED" while the persisted
     * state was the clean one. The load had read the old state before the reset and published it after.
     */
    @Test
    fun `a load that is running when the reset comes does not publish its old state over the reset`() {
        persisted = storedCompletedPod()
        val manager = newManager()
        val loadHasRead = CountDownLatch(1)
        val finishLoad = CountDownLatch(1)
        whenever(preferences.get(EquilStringKey.State)).thenAnswer {
            val read = persisted
            loadHasRead.countDown()
            finishLoad.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            read
        }

        val load = thread { manager.init() }
        assertThat(loadHasRead.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()
        val reset = thread { manager.clearPodState() }
        // The reset either waits for the load, or it is done now and the load publishes over it.
        reset.join(RESET_WAIT_MS)
        finishLoad.countDown()
        load.join()
        reset.join()

        assertThat(manager.isActivationCompleted()).isFalse()
    }

    @Test
    fun `a second init does not reload over newer in-memory state`() {
        sut.init()

        // An activation completes and is written through, exactly as the wizard does.
        sut.setActivationProgress(ActivationProgress.COMPLETED)
        assertThat(sut.isActivationCompleted()).isTrue()

        // Preferences go stale behind our back - what clearAllSharedPrefs does between instrumented
        // tests, and equally what any external write would do.
        persisted = ""

        // The plugin is started again. This used to reload and throw the completed activation away.
        sut.init()

        assertThat(sut.isActivationCompleted()).isTrue()
    }

    @Test
    fun `loading never publishes a null state, even for unparseable json`() {
        // loadPodState used to null the field first and fill it in afterwards. Any reader landing in
        // that window saw no pod at all - and the overview, the wizard and the command queue all read
        // this object while the BLE threads write to it.
        persisted = "{not json"

        sut.init()

        assertThat(sut.hasPodState()).isTrue()
        assertThat(sut.getActivationProgress()).isEqualTo(ActivationProgress.NONE)
    }

    private companion object {

        const val TIMEOUT_SECONDS = 5L
        const val RESET_WAIT_MS = 300L
    }
}

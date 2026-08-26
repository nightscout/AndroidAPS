package app.aaps.pump.equil.manager

import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
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
import app.aaps.pump.equil.keys.EquilStringKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import javax.inject.Provider

/**
 * How the pod state survives a plugin restart.
 *
 * `EquilPumpPlugin.onStart` runs on a background coroutine and calls [EquilManager.init], so it can
 * land at any moment - including while a pod activation is running. Reloading the state there discards
 * whatever is newer in memory, which is what made `EquilEmulatorActivationTest` fail two different ways
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
    @Mock lateinit var notificationManager: NotificationManager
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
        sut = EquilManager(
            aapsLogger, rxBus, preferences, rh, pumpSync, equilBLE, equilHistoryRecordDao,
            equilHistoryPumpDao, Provider { mock<PumpEnactResult>() }, dateUtil, notificationManager,
            ch, bolusProgressData
        )
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
}

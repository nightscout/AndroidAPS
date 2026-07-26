package app.aaps.pump.dana.compose

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.database.DanaHistoryRecord
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.dana.events.EventDanaRSyncStatus
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyByte
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit test for [DanaHistoryViewModel]. The init block runs synchronously in the constructor
 * (it builds the available-type list from the active pump's [PumpType], subscribes to the
 * sync-status rx stream and issues the first record load), so every collaborator it touches must
 * be stubbed. The pump is set to DANA_R (neither Korean nor RS) so the full non-Korean type list
 * is produced. Only deterministic synchronous outcomes are asserted; [DanaHistoryViewModel.reload]
 * uses viewModelScope and is deferred by the StandardTestDispatcher, so it is not exercised.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DanaHistoryViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var danaHistoryRecordDao: DanaHistoryRecordDao
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var aapsSchedulers: AapsSchedulers

    private val activePump: PumpWithConcentration = mock()

    private lateinit var sut: DanaHistoryViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())

        // Active pump drives the available-type list; DANA_R => not Korean, not RS
        whenever(activePlugin.activePump).thenReturn(activePump)
        whenever(activePump.pumpDescription).thenReturn(PumpDescription().apply { pumpType = PumpType.DANA_R })

        // All single-arg label lookups (stored in non-null PumpHistoryType.name -> NPE if null)
        whenever(rh.gs(anyInt())).thenReturn("")

        // rx wiring touched at construction
        whenever(rxBus.toObservable(EventDanaRSyncStatus::class.java)).thenReturn(Observable.empty())
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())

        // First record load issued in init (and again on every selectType)
        whenever(dateUtil.now()).thenReturn(0L)
        whenever(danaHistoryRecordDao.allFromByType(anyLong(), anyByte()))
            .thenReturn(Single.just(emptyList()))

        sut = DanaHistoryViewModel(
            aapsLogger, rh, activePlugin, commandQueue, danaHistoryRecordDao,
            dateUtil, decimalFormatter, profileUtil, rxBus, aapsSchedulers
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultState_buildsTypeList_andSelectsAlarmFirst() {
        val state = sut.uiState.value

        assertThat(state.availableTypes).isNotEmpty()
        assertThat(state.selectedType).isEqualTo(state.availableTypes.first())
        assertThat(state.selectedType?.type).isEqualTo(RecordTypes.RECORD_TYPE_ALARM)
        // DANA_R (not Korean, not RS) exposes the error/refill/suspend rows but not prime
        assertThat(state.availableTypes.map { it.type }).contains(RecordTypes.RECORD_TYPE_ERROR)
        assertThat(state.availableTypes.map { it.type }).contains(RecordTypes.RECORD_TYPE_REFILL)
        assertThat(state.availableTypes.map { it.type }).doesNotContain(RecordTypes.RECORD_TYPE_PRIME)
    }

    @Test
    fun selectType_updatesSelectedType() {
        val glucoseType = sut.uiState.value.availableTypes.first { it.type == RecordTypes.RECORD_TYPE_GLUCOSE }

        sut.selectType(glucoseType)

        assertThat(sut.uiState.value.selectedType).isEqualTo(glucoseType)
    }

    @Test
    fun formatValue_nonGlucoseType_usesDecimalFormatter() {
        whenever(decimalFormatter.to2Decimal(anyDouble())).thenReturn("1.23")
        val record = DanaHistoryRecord(timestamp = 1_000L, value = 1.23)

        // default selected type is ALARM (non-glucose) -> decimal path
        assertThat(sut.formatValue(record)).isEqualTo("1.23")
    }

    @Test
    fun formatValue_glucoseType_usesProfileUtil() {
        // targetUnits default resolves to profileUtil.units (null on a mock) -> match with anyOrNull()
        whenever(profileUtil.fromMgdlToStringInUnits(anyDouble(), anyOrNull())).thenReturn("5.5")
        val glucoseType = sut.uiState.value.availableTypes.first { it.type == RecordTypes.RECORD_TYPE_GLUCOSE }
        sut.selectType(glucoseType)
        val record = DanaHistoryRecord(timestamp = 1_000L, value = 100.0)

        assertThat(sut.formatValue(record)).isEqualTo("5.5")
    }

    @Test
    fun formatTime_nonDailyType_usesDateAndTime() {
        whenever(dateUtil.dateAndTimeString(anyLong())).thenReturn("2024-01-01 12:00")
        val record = DanaHistoryRecord(timestamp = 1_000L)

        // default selected type is ALARM (not DAILY) -> date-and-time path
        assertThat(sut.formatTime(record)).isEqualTo("2024-01-01 12:00")
    }

    @Test
    fun formatDailyTotal_sumsBolusAndBasal_viaTemplate() {
        whenever(rh.gs(anyInt(), anyOrNull())).thenReturn("6.00 U")
        val record = DanaHistoryRecord(timestamp = 1_000L, dailyBolus = 4.0, dailyBasal = 2.0)

        assertThat(sut.formatDailyTotal(record)).isEqualTo("6.00 U")
    }
}

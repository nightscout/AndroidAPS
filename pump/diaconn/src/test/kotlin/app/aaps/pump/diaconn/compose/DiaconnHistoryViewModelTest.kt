package app.aaps.pump.diaconn.compose

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.pump.diaconn.common.RecordTypes
import app.aaps.pump.diaconn.database.DiaconnHistoryRecord
import app.aaps.pump.diaconn.database.DiaconnHistoryRecordDao
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class DiaconnHistoryViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var aapsSchedulers: AapsSchedulers
    @Mock private lateinit var context: Context

    private val diaconnHistoryRecordDao: DiaconnHistoryRecordDao = mock()

    private lateinit var sut: DiaconnHistoryViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())
        // init runs synchronously (not in a launch): builds the type list, subscribes to status events,
        // and loads records for the first type — every collaborator it touches must be stubbed.
        whenever(rh.gs(anyInt())).thenReturn("")
        whenever(rxBus.toObservable(EventPumpStatusChanged::class.java)).thenReturn(Observable.empty())
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(dateUtil.now()).thenReturn(0L)
        whenever(diaconnHistoryRecordDao.allFromByType(anyLong(), anyByte())).thenReturn(Single.just(emptyList()))
        sut = DiaconnHistoryViewModel(
            aapsLogger, rh, commandQueue, diaconnHistoryRecordDao, dateUtil, decimalFormatter, rxBus,
            aapsSchedulers, context
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `init selects the alarm type first and builds the full type list`() {
        val state = sut.uiState.value
        assertThat(state.availableTypes).hasSize(7)
        assertThat(state.selectedType?.type).isEqualTo(RecordTypes.RECORD_TYPE_ALARM)
    }

    @Test
    fun `selectType updates the selected type`() {
        val bolusType = sut.uiState.value.availableTypes.first { it.type == RecordTypes.RECORD_TYPE_BOLUS }
        sut.selectType(bolusType)

        assertThat(sut.uiState.value.selectedType?.type).isEqualTo(RecordTypes.RECORD_TYPE_BOLUS)
    }

    @Test
    fun `formatValue delegates to the decimal formatter`() {
        whenever(decimalFormatter.to2Decimal(anyDouble())).thenReturn("1.23")
        val record = mock<DiaconnHistoryRecord> { whenever(it.value).thenReturn(1.23) }

        assertThat(sut.formatValue(record)).isEqualTo("1.23")
    }

    @Test
    fun `formatTime uses the date-and-time string for the alarm type`() {
        whenever(dateUtil.dateAndTimeString(anyLong())).thenReturn("2026-06-30 12:00")
        val record = mock<DiaconnHistoryRecord> { whenever(it.timestamp).thenReturn(1_000L) }

        assertThat(sut.formatTime(record)).isEqualTo("2026-06-30 12:00")
    }
}

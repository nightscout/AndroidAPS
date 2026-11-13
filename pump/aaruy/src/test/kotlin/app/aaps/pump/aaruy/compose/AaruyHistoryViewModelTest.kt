package app.aaps.pump.aaruy.compose

import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.pump.PumpHistoryType
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfo
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.database.HistoryUnitInfo
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class AaruyHistoryViewModelTest {

    private val now = 1_800_000_000_000L
    private val rh: ResourceHelper = mock()
    private val dateUtil: DateUtil = mock()
    private val historyDao: HistoryUnitInfoDao = mock()
    private val dailyDao: HistoryDailyUnitInfoDao = mock()
    private val pump = AaruyPump(mock<AAPSLogger>(), rh, mock<Preferences>(), dateUtil, mock<PumpSync>(), mock<TemporaryBasalStorage>(), mock<BolusProgressData>())

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(dateUtil.now()).thenReturn(now)
        whenever(rh.gs(any<Int>())).thenAnswer { "s${it.arguments[0]}" }
        whenever(rh.gs(R.string.alarm_block)).thenReturn("Motor blocked")
        whenever(historyDao.querySince(any())).thenReturn(emptyList())
        whenever(dailyDao.queryAll()).thenReturn(emptyList())
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /** As the real one does for U200: insulin units, and the pump units next to them. */
    private val ch: ConcentrationHelper = mock<ConcentrationHelper>().also {
        whenever(it.insulinAmountString(any())).thenAnswer { invocation -> (invocation.arguments[0] as PumpInsulin).cU.let { cU -> "%.2f U (%.2f CU)".format(Locale.ROOT, cU * 2, cU) } }
        whenever(it.basalRateString(any(), any(), any())).thenAnswer { invocation -> (invocation.arguments[0] as PumpRate).cU.let { cU -> "%.2f U/h (%.2f CU/h)".format(Locale.ROOT, cU * 2, cU) } }
    }

    private val commandQueue: CommandQueue = mock()

    private fun viewModel() = AaruyHistoryViewModel(rh, commandQueue, pump, historyDao, dailyDao, dateUtil, ch)

    /** The queue owns the link. The reload is its status read, and the list is read after that read has ended. */
    @Test
    fun aReloadReadsThePumpThroughTheQueueAndThenTheList() {
        val viewModel = viewModel()
        // The first list, before the reload.
        verify(historyDao, timeout(5_000)).querySince(any())
        runBlocking {
            whenever(commandQueue.readStatus(any())).thenAnswer {
                whenever(historyDao.querySince(any())).thenReturn(listOf(entry(now - 1000, AaruyHistoryTypes.BOLUS.toInt(), data = 1000)))
                mock<PumpEnactResult>()
            }
        }
        viewModel.reload()
        viewModel.recordsWhen { it.isNotEmpty() }
        runBlocking { verify(commandQueue).readStatus(any()) }
        val end = System.currentTimeMillis() + 5_000
        while (viewModel.uiState.value.isLoading) {
            assertTrue(System.currentTimeMillis() < end, "still loading")
            Thread.sleep(10)
        }
    }

    /** With U200 a bolus of 1.0 CU is 2.0 U of insulin. Shown as "1.00 U", the user read half of what was given. */
    @Test
    fun withU200TheHistoryShowsInsulinUnitsAndPumpUnits() {
        whenever(historyDao.querySince(any())).thenReturn(listOf(entry(now - 1000, AaruyHistoryTypes.BOLUS.toInt(), data = 1000)))
        val viewModel = viewModel()
        viewModel.show(AaruyHistoryTypes.BOLUS)
        val records = viewModel.recordsWhen { it.isNotEmpty() }
        assertEquals("2.00 U (1.00 CU)", viewModel.valueText(records.single()))
    }

    private fun entry(time: Long, type: Int, data: Long = 0, injectTime: Long = 0, delayBolus: Long = 0) = HistoryUnitInfo().also {
        it.time = time
        it.type = type
        it.data = data
        it.injectTime = injectTime
        it.delayBolus = delayBolus
    }

    /** The list is read on another thread. */
    private fun AaruyHistoryViewModel.recordsWhen(condition: (List<AaruyHistoryRecord>) -> Boolean): List<AaruyHistoryRecord> {
        val end = System.currentTimeMillis() + 5_000
        while (!condition(uiState.value.records)) {
            assertTrue(System.currentTimeMillis() < end, "records: ${uiState.value.records}")
            Thread.sleep(10)
        }
        return uiState.value.records
    }

    private fun AaruyHistoryViewModel.show(type: Byte) = selectType(uiState.value.availableTypes.first { it.type == type })

    /** Seen on a real pump: the log keeps only the low byte, a blocked motor is 5. It showed as "None". */
    @Test
    fun anAlarmFromThePumpLogHasItsName() {
        whenever(historyDao.querySince(any())).thenReturn(listOf(entry(now - 1000, AaruyHistoryTypes.ALARM.toInt(), data = 5)))
        val viewModel = viewModel()
        viewModel.show(AaruyHistoryTypes.ALARM)
        val records = viewModel.recordsWhen { it.isNotEmpty() }
        assertEquals("Motor blocked", viewModel.valueText(records.single()))
    }

    @Test
    fun aBolusCountsItsExtendedPart() {
        whenever(historyDao.querySince(any())).thenReturn(listOf(entry(now - 1000, AaruyHistoryTypes.BOLUS.toInt(), data = 1500, delayBolus = 500)))
        val records = viewModel().recordsWhen { it.isNotEmpty() }
        assertEquals(2.0, records.single().value, 0.0001)
    }

    @Test
    fun aReservoirChangeShowsRewindAndPrimeAndNothingElse() {
        whenever(historyDao.querySince(any())).thenReturn(
            listOf(
                entry(now - 3000, AaruyHistoryTypes.REWIND.toInt()),
                entry(now - 2000, AaruyHistoryTypes.PRIME.toInt(), data = 420),
                entry(now - 1000, AaruyHistoryTypes.BOLUS.toInt(), data = 1000)
            )
        )
        val viewModel = viewModel()
        viewModel.show(AaruyHistoryTypes.REPLACED)
        val records = viewModel.recordsWhen { it.size == 2 }
        assertEquals(listOf("s${R.string.actions_reservoir_prime}", "s${R.string.aaruy_rewind}"), records.map { viewModel.valueText(it) })
    }

    /** Review #69: the driver writes this row when a new pump is paired, and no list showed it. */
    @Test
    fun aNewPumpIsShownWithTheChangesOfReservoir() {
        whenever(historyDao.querySince(any())).thenReturn(listOf(entry(now - 1000, AaruyHistoryTypes.NEW_PUMP.toInt()).also { it.blueName = "X10001Y" }))
        val viewModel = viewModel()
        viewModel.show(AaruyHistoryTypes.REPLACED)
        val record = viewModel.recordsWhen { it.isNotEmpty() }.single()
        assertEquals("s${R.string.aaruy_pairing_successful}", viewModel.valueText(record))
        assertEquals("X10001Y", viewModel.detailText(record))
    }

    /**
     * Review #68, #81: a day of the pump starts at midnight in UTC+8, in Prague 17:00 of the day before. With one date it had
     * the date before, and with the date of the pump the date after in the evening. So it shows its local start and end.
     */
    @Test
    fun aDailyTotalShowsTheLocalStartAndEndOfThePumpDay() {
        val start = 1_794_067_200_000L // 7 Nov 2026 16:00 UTC, midnight of 8 Nov in UTC+8
        val end = start + 24 * 60 * 60 * 1000L
        whenever(dateUtil.dateStringShort(start)).thenReturn("11/7")
        whenever(dateUtil.timeString(start)).thenReturn("17:00")
        whenever(dateUtil.dateStringShort(end)).thenReturn("11/8")
        whenever(dateUtil.timeString(end)).thenReturn("17:00")
        whenever(rh.gs(R.string.aaruy_history_pump_day, "11/7", "17:00", "11/8", "17:00")).thenReturn("11/7 17:00 – 11/8 17:00")
        assertEquals("11/7 17:00 – 11/8 17:00", viewModel().timeText(AaruyHistoryRecord(start, AaruyHistoryTypes.DAILY)))
    }

    /** The first list is read on another thread. Read last, it replaced the list of the type the user chose after it. */
    @Test
    fun aSlowerReadOfTheTypeBeforeDoesNotReplaceTheListOfTheTypeNow() {
        whenever(historyDao.querySince(any())).thenAnswer {
            Thread.sleep(500)
            emptyList<HistoryUnitInfo>()
        }
        whenever(dailyDao.queryAll()).thenReturn(listOf(HistoryDailyUnitInfo().also {
            it.dateTimestamp = now - 1000
            it.dailyTotal = 145
            it.dailyBolus = 111
        }))
        val viewModel = viewModel()
        viewModel.show(AaruyHistoryTypes.DAILY)
        viewModel.recordsWhen { it.isNotEmpty() }
        // The read of the bolus list ends now.
        Thread.sleep(800)
        assertEquals(1, viewModel.uiState.value.records.size)
    }

    @Test
    fun aDayIsSplitIntoBolusAndBasal() {
        whenever(dailyDao.queryAll()).thenReturn(listOf(HistoryDailyUnitInfo().also {
            it.dateTimestamp = now - 1000
            it.dailyTotal = 145
            it.dailyBolus = 111
        }))
        val viewModel = viewModel()
        viewModel.show(AaruyHistoryTypes.DAILY)
        val day = viewModel.recordsWhen { it.isNotEmpty() }.single()
        assertEquals(11.1, day.dailyBolus, 0.0001)
        assertEquals(3.4, day.dailyBasal, 0.0001)
    }

    /** Equal rows would give equal list keys, and that crashes the list. */
    @Test
    fun equalRowsComeOnceAndTheNewestFirst() {
        whenever(historyDao.querySince(any())).thenReturn(
            listOf(
                entry(now - 5000, AaruyHistoryTypes.BOLUS.toInt(), data = 1000),
                entry(now - 1000, AaruyHistoryTypes.BOLUS.toInt(), data = 500),
                entry(now - 5000, AaruyHistoryTypes.BOLUS.toInt(), data = 1000)
            )
        )
        val records = viewModel().recordsWhen { it.isNotEmpty() }
        assertEquals(listOf(now - 1000, now - 5000), records.map { it.timestamp })
    }

    @Test
    fun theTypesAreTheOnesTheScreenOffers() {
        val types = viewModel().uiState.value.availableTypes.map(PumpHistoryType::type)
        assertEquals(
            listOf(AaruyHistoryTypes.BOLUS, AaruyHistoryTypes.TEMP_BASAL, AaruyHistoryTypes.BASAL, AaruyHistoryTypes.ALARM, AaruyHistoryTypes.REPLACED, AaruyHistoryTypes.DAILY),
            types
        )
    }
}

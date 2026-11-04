package app.aaps.pump.equil.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.R
import app.aaps.pump.equil.database.EquilHistoryPump
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.database.ResolvedResult
import app.aaps.pump.equil.driver.definition.EquilHistoryEntryGroup
import app.aaps.pump.equil.events.EventEquilDataChanged
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit test for [EquilHistoryViewModel].
 *
 * Setting Main to a [StandardTestDispatcher] DEFERS the `rxBus.toFlow(...).launchIn(viewModelScope)`
 * subscription in `init`, so we assert only the synchronous default state and the pure
 * setter/companion functions. `loadData()` explicitly launches on `Dispatchers.IO` inside a
 * try/catch, so its background work cannot affect the deterministic values we assert here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class EquilHistoryViewModelTest {

    @Mock private lateinit var equilHistoryRecordDao: EquilHistoryRecordDao
    @Mock private lateinit var equilHistoryPumpDao: EquilHistoryPumpDao
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var profileUtil: ProfileUtil

    private val equilPumpPlugin: EquilPumpPlugin = mock()

    private lateinit var sut: EquilHistoryViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())

        // init evaluates rxBus.toFlow(...) synchronously (before the deferred launchIn) -> must stub
        whenever(rxBus.toFlow(EventEquilDataChanged::class)).thenReturn(emptyFlow())

        sut = EquilHistoryViewModel(
            equilHistoryRecordDao,
            equilHistoryPumpDao,
            equilPumpPlugin,
            dateUtil,
            aapsLogger,
            rxBus,
            profileUtil
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultState_isAllGroupAndEmpty() {
        assertThat(sut.selectedGroup.value).isEqualTo(EquilHistoryEntryGroup.All)
        assertThat(sut.filteredCommandHistory.value).isEmpty()
        assertThat(sut.pumpEvents.value).isEmpty()
    }

    @Test
    fun setFilter_updatesSelectedGroup() {
        sut.setFilter(EquilHistoryEntryGroup.Bolus)
        assertThat(sut.selectedGroup.value).isEqualTo(EquilHistoryEntryGroup.Bolus)

        sut.setFilter(EquilHistoryEntryGroup.Basal)
        assertThat(sut.selectedGroup.value).isEqualTo(EquilHistoryEntryGroup.Basal)
    }

    @Test
    fun groupForType_mapsEventTypesToGroups() {
        assertThat(EquilHistoryViewModel.groupForType(EquilHistoryRecord.EventType.SET_BOLUS))
            .isEqualTo(EquilHistoryEntryGroup.Bolus)
        assertThat(EquilHistoryViewModel.groupForType(EquilHistoryRecord.EventType.CANCEL_BOLUS))
            .isEqualTo(EquilHistoryEntryGroup.Bolus)
        assertThat(EquilHistoryViewModel.groupForType(EquilHistoryRecord.EventType.SET_TEMPORARY_BASAL))
            .isEqualTo(EquilHistoryEntryGroup.Basal)
        assertThat(EquilHistoryViewModel.groupForType(EquilHistoryRecord.EventType.INSERT_CANNULA))
            .isEqualTo(EquilHistoryEntryGroup.Pair)
        assertThat(EquilHistoryViewModel.groupForType(EquilHistoryRecord.EventType.SET_TIME))
            .isEqualTo(EquilHistoryEntryGroup.Configuration)
        // types absent from the when fall through to All
        assertThat(EquilHistoryViewModel.groupForType(EquilHistoryRecord.EventType.FILL))
            .isEqualTo(EquilHistoryEntryGroup.All)
    }

    /** A pump history row. Port 4, type 1, level 1 is a pump event with a text, so it gives an Event item. */
    private fun pumpRow(id: Long, index: Int, time: Long, rate: Int = 100, largeRate: Int = 0, withEvent: Boolean = true) =
        EquilHistoryPump().apply {
            this.id = id
            eventIndex = index
            eventTimestamp = time
            this.rate = rate
            this.largeRate = largeRate
            if (withEvent) {
                port = 4
                type = 1
                level = 1
            }
        }

    /**
     * The insert never replaces a row (the row id is generated), so the same pump event can be stored
     * twice. On 4.0.0-rc1 the two copies gave two equal items, and the list crashed on the duplicate key.
     */
    @Test
    fun transformPumpEvents_showsAnEventStoredTwiceOnlyOnce() {
        val event = pumpRow(id = 1, index = 7, time = 1_000_007)
        val copy = pumpRow(id = 2, index = 7, time = 1_000_007)

        val items = sut.transformPumpEvents(listOf(event, copy))

        assertThat(items.filterIsInstance<PumpEventItem.Event>()).hasSize(1)
    }

    /** The list key does not depend on time or content, so two items that look the same still get different keys. */
    @Test
    fun transformPumpEvents_keysStayUniqueWhenTwoEventsLookTheSame() {
        val first = pumpRow(id = 1, index = 5, time = 1_000_000)
        val second = pumpRow(id = 2, index = 6, time = 1_000_000)

        val items = sut.transformPumpEvents(listOf(first, second))

        assertThat(items.filterIsInstance<PumpEventItem.Event>()).hasSize(2)
        assertThat(items.map { it.listKey }).containsNoDuplicates()
    }

    /** Basal changes, a bolus and events from the same rows: every item still has its own key. */
    @Test
    fun transformPumpEvents_givesEveryKindItsOwnKey() {
        val rows = listOf(
            pumpRow(id = 1, index = 1, time = 1_000_001, rate = 100),
            pumpRow(id = 2, index = 2, time = 2_000_002, rate = 100, largeRate = 50, withEvent = false),
            pumpRow(id = 3, index = 3, time = 3_000_003, rate = 200)
        )

        val items = sut.transformPumpEvents(rows)

        assertThat(items.filterIsInstance<PumpEventItem.BasalChange>()).hasSize(2)
        assertThat(items.filterIsInstance<PumpEventItem.Bolus>()).hasSize(1)
        assertThat(items.filterIsInstance<PumpEventItem.Event>()).hasSize(2)
        assertThat(items.map { it.listKey }).containsNoDuplicates()
    }

    @Test
    fun failureStringRes_mapsResolvedResultToStringRes() {
        assertThat(EquilHistoryViewModel.failureStringRes(ResolvedResult.NOT_FOUNT))
            .isEqualTo(R.string.equil_command_not_found)
        assertThat(EquilHistoryViewModel.failureStringRes(ResolvedResult.CONNECT_ERROR))
            .isEqualTo(R.string.equil_command_connect_error)
        assertThat(EquilHistoryViewModel.failureStringRes(ResolvedResult.FAILURE))
            .isEqualTo(R.string.equil_command_connect_no_response)
        assertThat(EquilHistoryViewModel.failureStringRes(ResolvedResult.SUCCESS))
            .isEqualTo(R.string.equil_success)
        assertThat(EquilHistoryViewModel.failureStringRes(ResolvedResult.NONE))
            .isEqualTo(R.string.equil_none)
        assertThat(EquilHistoryViewModel.failureStringRes(null))
            .isEqualTo(R.string.equil_command__unknown)
    }
}

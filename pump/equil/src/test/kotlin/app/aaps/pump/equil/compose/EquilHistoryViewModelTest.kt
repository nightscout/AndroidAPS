package app.aaps.pump.equil.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.R
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
        whenever(rxBus.toFlow(EventEquilDataChanged::class.java)).thenReturn(emptyFlow())

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

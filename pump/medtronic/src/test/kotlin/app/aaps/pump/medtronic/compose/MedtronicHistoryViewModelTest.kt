package app.aaps.pump.medtronic.compose

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.defs.PumpHistoryEntryGroup
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntry
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntryType
import app.aaps.pump.medtronic.data.MedtronicHistoryData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Unit test for [MedtronicHistoryViewModel]. The init block synchronously calls filterHistory(All),
 * which reads [MedtronicHistoryData.allHistory], so that getter is stubbed before construction.
 * Tests cover the default (All) state assembly and the pure selectGroup() filtering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MedtronicHistoryViewModelTest {

    @Mock private lateinit var rh: ResourceHelper

    private val medtronicHistoryData: MedtronicHistoryData = mock()

    // Bolus -> PumpHistoryEntryGroup.Bolus ; Rewind -> PumpHistoryEntryGroup.Prime
    private val bolusEntry: PumpHistoryEntry = mock()
    private val primeEntry: PumpHistoryEntry = mock()

    private lateinit var sut: MedtronicHistoryViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())

        whenever(bolusEntry.entryType).thenReturn(PumpHistoryEntryType.Bolus)
        whenever(primeEntry.entryType).thenReturn(PumpHistoryEntryType.Rewind)
        // Read synchronously in init { filterHistory(All) } and again on every selectGroup()
        whenever(medtronicHistoryData.allHistory).thenReturn(mutableListOf(bolusEntry, primeEntry))

        sut = MedtronicHistoryViewModel(rh, medtronicHistoryData)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultState_selectsAllGroup_andKeepsEveryRecord() {
        val state = sut.uiState.value

        assertThat(state.selectedGroup).isEqualTo(PumpHistoryEntryGroup.All)
        assertThat(state.records).containsExactly(bolusEntry, primeEntry).inOrder()
    }

    @Test
    fun selectGroup_bolus_filtersToMatchingRecordsOnly() {
        sut.selectGroup(PumpHistoryEntryGroup.Bolus)

        val state = sut.uiState.value
        assertThat(state.selectedGroup).isEqualTo(PumpHistoryEntryGroup.Bolus)
        assertThat(state.records).containsExactly(bolusEntry)
    }

    @Test
    fun selectGroup_prime_filtersToMatchingRecordsOnly() {
        sut.selectGroup(PumpHistoryEntryGroup.Prime)

        val state = sut.uiState.value
        assertThat(state.selectedGroup).isEqualTo(PumpHistoryEntryGroup.Prime)
        assertThat(state.records).containsExactly(primeEntry)
    }

    @Test
    fun selectGroup_withNoMatches_yieldsEmptyRecordsButUpdatesSelection() {
        sut.selectGroup(PumpHistoryEntryGroup.Glucose)

        val state = sut.uiState.value
        assertThat(state.selectedGroup).isEqualTo(PumpHistoryEntryGroup.Glucose)
        assertThat(state.records).isEmpty()
    }

    @Test
    fun groups_containsAllFilterOption() {
        assertThat(sut.groups).contains(PumpHistoryEntryGroup.All)
    }
}

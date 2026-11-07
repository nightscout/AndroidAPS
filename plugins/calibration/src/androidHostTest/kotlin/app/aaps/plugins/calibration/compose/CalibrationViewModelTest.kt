package app.aaps.plugins.calibration.compose

import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Default-state unit test for [CalibrationViewModel].
 *
 * The VM uses [androidx.lifecycle.viewModelScope], so [UnconfinedTestDispatcher] is installed as
 * Main to back it. Under Unconfined the init observeChanges chain settles in-line: the onStart
 * trigger flows through the merge of the two (empty) change streams, the debounce releases on
 * upstream completion, and recomputeSuspend runs once against the mocked collaborators. With no
 * sensor session present (getLastTherapyRecordUpToNow == null) the recompute yields the
 * empty-session snapshot, so every structural field stays at its default.
 *
 * Mirrors the JUnit5 + setMain convention of the sync-module ViewModel tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CalibrationViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var rh: TextResolver
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var config: Config
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var preferences: Preferences

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Stub every collaborator the init recompute touches when no session exists.
        whenever(persistenceLayer.observeChanges(CAL::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TE::class)).thenReturn(emptyFlow())
        whenever(dateUtil.now()).thenReturn(0L)
        whenever(profileUtil.units).thenReturn(GlucoseUnit.MGDL)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        CalibrationViewModel(persistenceLayer, profileUtil, dateUtil, rh, rxBus, config, batchExecutor, preferences)

    @Test
    fun uiState_hasEmptySessionDefaults() {
        val sut = viewModel()

        val state = sut.uiState.value
        assertThat(state.sessionStart).isNull()
        assertThat(state.warmUpEndsAt).isNull()
        assertThat(state.isInWarmUp).isFalse()
        assertThat(state.entries).isEmpty()
        assertThat(state.fit).isNull()
        assertThat(state.selectedEntryId).isNull()
        assertThat(state.glucoseUnit).isEqualTo(GlucoseUnit.MGDL)
    }

    @Test
    fun selectEntry_updatesSelectedEntryId() {
        val sut = viewModel()

        sut.selectEntry(42L)

        assertThat(sut.uiState.value.selectedEntryId).isEqualTo(42L)
    }

    @Test
    fun requestDelete_ofAnUnknownEntry_asksNothing() = runTest {
        val sut = viewModel()

        sut.requestDelete(42L)

        verify(batchExecutor, never()).prepare(any(), any(), any())
    }
}

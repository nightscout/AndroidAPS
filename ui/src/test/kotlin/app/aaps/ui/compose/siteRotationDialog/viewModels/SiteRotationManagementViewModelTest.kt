package app.aaps.ui.compose.siteRotationDialog.viewModels

import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.siteRotation.BodyType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class SiteRotationManagementViewModelTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var translator: Translator
    @Mock private lateinit var aapsLogger: AAPSLogger

    private lateinit var sut: SiteRotationManagementViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers the init{} loadEntries() launch (no advanceUntilIdle), so construction
        // stays clean and we test the synchronous setters against the default state. The cold flow chains built
        // synchronously in setupEventListeners() must be stubbed to non-null flows or construction NPEs.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(persistenceLayer.observeChanges(TE::class.java)).thenReturn(emptyFlow())
        whenever(preferences.observe(IntKey.SiteRotationUserProfile)).thenReturn(MutableStateFlow(0))
        whenever(preferences.get(IntKey.SiteRotationUserProfile)).thenReturn(0)
        sut = SiteRotationManagementViewModel(
            rh, dateUtil, persistenceLayer, batchExecutor, preferences, translator, aapsLogger,
            CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState shows all sites with no location filter`() {
        val state = sut.uiState.value
        assertThat(state.showPumpSites).isTrue()
        assertThat(state.showCgmSites).isTrue()
        assertThat(state.selectedLocation).isEqualTo(TE.Location.NONE)
        assertThat(state.showBodyType).isEqualTo(BodyType.MAN)
        assertThat(state.editedTe).isNull()
    }

    @Test
    fun `selectLocation sets the selected location`() {
        sut.selectLocation(TE.Location.SIDE_RIGHT_UPPER_ARM)

        assertThat(sut.uiState.value.selectedLocation).isEqualTo(TE.Location.SIDE_RIGHT_UPPER_ARM)
    }

    @Test
    fun `setShowPumpSites and setShowCgmSites toggle the view filters`() {
        sut.setShowPumpSites(false)
        sut.setShowCgmSites(false)

        val state = sut.uiState.value
        assertThat(state.showPumpSites).isFalse()
        assertThat(state.showCgmSites).isFalse()
    }

    @Test
    fun `updateEditLocation sets selectedLocation and marks edited`() {
        sut.updateEditLocation(TE.Location.SIDE_RIGHT_UPPER_ARM)

        val state = sut.uiState.value
        assertThat(state.selectedLocation).isEqualTo(TE.Location.SIDE_RIGHT_UPPER_ARM)
        assertThat(state.isEdited).isTrue()
    }

    @Test
    fun `onZoneClick toggles the location filter on and off`() {
        sut.onZoneClick(TE.Location.SIDE_RIGHT_UPPER_ARM)
        assertThat(sut.uiState.value.selectedLocation).isEqualTo(TE.Location.SIDE_RIGHT_UPPER_ARM)

        sut.onZoneClick(TE.Location.SIDE_RIGHT_UPPER_ARM)
        assertThat(sut.uiState.value.selectedLocation).isEqualTo(TE.Location.NONE)
    }

    @Test
    fun `cancelEditing clears editing state and resets location`() {
        sut.updateEditLocation(TE.Location.SIDE_RIGHT_UPPER_ARM)

        sut.cancelEditing()

        val state = sut.uiState.value
        assertThat(state.editedTe).isNull()
        assertThat(state.isEdited).isFalse()
        assertThat(state.selectedLocation).isEqualTo(TE.Location.NONE)
    }
}

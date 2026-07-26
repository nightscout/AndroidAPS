package app.aaps.ui.compose.treatments.viewmodels

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.PS
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class ProfileSwitchViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rxBus: RxBus

    private lateinit var sut: ProfileSwitchViewModel

    // Distinct ProfileSealed items via distinct wrapped PS mocks (data-class value equality).
    private fun link() = ProfileSealed.PS(value = mock<PS>(), activePlugin = null)

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(persistenceLayer.observeChanges(PS::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(EPS::class.java)).thenReturn(emptyFlow())
        sut = ProfileSwitchViewModel(persistenceLayer, profileRepository, rh, dateUtil, aapsLogger, rxBus)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState is not in removing mode and has no selection`() {
        val state = sut.uiState.value
        assertThat(state.isRemovingMode).isFalse()
        assertThat(state.selectedItems).isEmpty()
        assertThat(state.profileSwitches).isEmpty()
        assertThat(state.showInvalidated).isFalse()
    }

    @Test
    fun `enterSelectionMode selects the item and enables removing mode`() {
        val item = link()

        sut.enterSelectionMode(item)

        assertThat(sut.uiState.value.isRemovingMode).isTrue()
        assertThat(sut.uiState.value.selectedItems).containsExactly(item)
    }

    @Test
    fun `exitSelectionMode clears selection`() {
        sut.enterSelectionMode(link())

        sut.exitSelectionMode()

        assertThat(sut.uiState.value.isRemovingMode).isFalse()
        assertThat(sut.uiState.value.selectedItems).isEmpty()
    }

    @Test
    fun `toggleSelection adds then removes an item`() {
        val first = link()
        val second = link()
        sut.enterSelectionMode(first)

        sut.toggleSelection(second)
        assertThat(sut.uiState.value.selectedItems).containsExactly(first, second)

        sut.toggleSelection(second)
        assertThat(sut.uiState.value.selectedItems).containsExactly(first)
    }

    @Test
    fun `toggleInvalidated flips the flag`() {
        sut.toggleInvalidated()
        assertThat(sut.uiState.value.showInvalidated).isTrue()
    }

    @Test
    fun `getDeleteConfirmationMessage empty when nothing selected, plural for many`() {
        assertThat(sut.getDeleteConfirmationMessage()).isEqualTo("")

        whenever(rh.gs(R.string.confirm_remove_multiple_items, 2)).thenReturn("Remove 2 items")
        sut.enterSelectionMode(link())
        sut.toggleSelection(link())
        assertThat(sut.getDeleteConfirmationMessage()).isEqualTo("Remove 2 items")
    }
}

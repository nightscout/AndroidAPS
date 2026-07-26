package app.aaps.ui.compose.treatments.viewmodels

import app.aaps.core.data.model.UE
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class UserEntryViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rxBus: RxBus

    private lateinit var sut: UserEntryViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(persistenceLayer.observeChanges(UE::class.java)).thenReturn(emptyFlow())
        sut = UserEntryViewModel(persistenceLayer, rh, dateUtil, aapsLogger, rxBus)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState has no entries and loop hidden`() {
        val state = sut.uiState.value
        assertThat(state.userEntries).isEmpty()
        assertThat(state.showLoop).isFalse()
    }

    @Test
    fun `toggleLoop flips the showLoop flag`() {
        sut.toggleLoop()
        assertThat(sut.uiState.value.showLoop).isTrue()

        sut.toggleLoop()
        assertThat(sut.uiState.value.showLoop).isFalse()
    }
}

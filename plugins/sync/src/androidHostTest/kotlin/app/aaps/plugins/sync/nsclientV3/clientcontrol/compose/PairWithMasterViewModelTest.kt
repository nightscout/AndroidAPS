package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.nssdk.localmodel.clientcontrol.MasterPairing
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlPublisher
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientPairingRepository
import app.aaps.plugins.sync.nsclientV3.clientcontrol.PairingOfferFetcher
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit test for [PairWithMasterViewModel]. Its init is fully synchronous (initialState() only reads
 * [ClientPairingRepository.currentPairing]); the async work (onPinEntered / confirmPair) is dispatched
 * on viewModelScope, so a StandardTestDispatcher-as-Main keeps those coroutine bodies parked and lets
 * us assert only the deterministic synchronous state transitions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PairWithMasterViewModelTest {

    @Mock private lateinit var repository: ClientPairingRepository
    @Mock private lateinit var publisher: ClientControlPublisher
    @Mock private lateinit var fetcher: PairingOfferFetcher
    @Mock private lateinit var dateUtil: DateUtil

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher does NOT auto-run viewModelScope.launch bodies (no advanceUntilIdle),
        // so onPinEntered/confirmPair only apply their synchronous prelude.
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = PairWithMasterViewModel(repository, publisher, fetcher, dateUtil)

    @Test
    fun `default state is PinEntry when not paired`() {
        // repository.currentPairing() returns null by default on the mock -> PinEntry
        val state = createViewModel().state.value

        assertThat(state).isEqualTo(PairWithMasterViewModel.UiState.PinEntry)
    }

    @Test
    fun `existing pairing yields AlreadyPaired`() {
        val pairing = MasterPairing(masterInstallId = "m1", clientId = "c1", masterSecretEnc = "enc")
        whenever(repository.currentPairing()).thenReturn(pairing)

        val state = createViewModel().state.value

        assertThat(state).isInstanceOf(PairWithMasterViewModel.UiState.AlreadyPaired::class.java)
        assertThat((state as PairWithMasterViewModel.UiState.AlreadyPaired).pairing).isEqualTo(pairing)
    }

    @Test
    fun `onPinEntered from PinEntry moves to Fetching synchronously`() {
        val sut = createViewModel()

        sut.onPinEntered("12345678")

        // The launch body (fetcher.findOfferForPin) is parked on the test dispatcher; only the
        // synchronous transition to Fetching is observable here.
        assertThat(sut.state.value).isEqualTo(PairWithMasterViewModel.UiState.Fetching)
    }

    @Test
    fun `onPinEntered is ignored when not in PinEntry`() {
        val pairing = MasterPairing(masterInstallId = "m1", clientId = "c1", masterSecretEnc = "enc")
        whenever(repository.currentPairing()).thenReturn(pairing)
        val sut = createViewModel()

        sut.onPinEntered("12345678")

        assertThat(sut.state.value).isInstanceOf(PairWithMasterViewModel.UiState.AlreadyPaired::class.java)
    }

    @Test
    fun `resumePinEntry returns to PinEntry`() {
        val sut = createViewModel()
        sut.onPinEntered("12345678") // -> Fetching

        sut.resumePinEntry()

        assertThat(sut.state.value).isEqualTo(PairWithMasterViewModel.UiState.PinEntry)
    }

    @Test
    fun `cancelConfirmation from non-Confirming state is a no-op`() {
        val sut = createViewModel() // PinEntry

        sut.cancelConfirmation()

        assertThat(sut.state.value).isEqualTo(PairWithMasterViewModel.UiState.PinEntry)
    }

    @Test
    fun `unpair wipes the pairing and returns to PinEntry`() {
        val pairing = MasterPairing(masterInstallId = "m1", clientId = "c1", masterSecretEnc = "enc")
        whenever(repository.currentPairing()).thenReturn(pairing)
        val sut = createViewModel() // AlreadyPaired

        sut.unpair()

        verify(repository).unpair()
        assertThat(sut.state.value).isEqualTo(PairWithMasterViewModel.UiState.PinEntry)
    }
}

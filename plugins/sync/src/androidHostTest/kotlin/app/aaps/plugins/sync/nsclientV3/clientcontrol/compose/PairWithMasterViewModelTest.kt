package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.nssdk.localmodel.clientcontrol.MasterPairing
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlPublisher
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientPairingRepository
import app.aaps.plugins.sync.nsclientV3.clientcontrol.PairingOfferFetcher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit test for [PairWithMasterViewModel].
 *
 * Its init is no longer synchronous: [ClientPairingRepository.currentPairing] suspends, so the state
 * starts at Fetching and resolves afterwards - `createViewModel` drains that. The rest of the async
 * work (onPinEntered / confirmPair / unpair) is dispatched on viewModelScope, and a
 * StandardTestDispatcher-as-Main keeps those bodies parked, so a test can still assert the
 * deterministic synchronous transition before advancing.
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
        // runTest drains anything still parked when the test body ends, so the fetcher needs a
        // defined answer even for tests that only assert the synchronous transition. Unstubbed it
        // returns null, and the `when` over Result has no else branch.
        runBlocking { whenever(fetcher.findOfferForPin(any())).thenReturn(PairingOfferFetcher.Result.NoMatch) }
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Builds the view model and lets its init block finish.
     *
     * The stored pairing is read asynchronously now - currentPairing() suspends, because the
     * repository takes a mutex across three preference reads - so the state starts at Fetching and
     * resolves to AlreadyPaired or PinEntry a moment later. StandardTestDispatcher parks that body
     * until the queue is drained, hence the advanceUntilIdle here rather than in every test.
     */
    private fun TestScope.createViewModel() =
        PairWithMasterViewModel(repository, publisher, fetcher, dateUtil).also { advanceUntilIdle() }

    @Test
    fun `default state is PinEntry when not paired`() = runTest {
        // repository.currentPairing() returns null by default on the mock -> PinEntry
        val state = createViewModel().state.value

        assertThat(state).isEqualTo(PairWithMasterViewModel.UiState.PinEntry)
    }

    @Test
    fun `existing pairing yields AlreadyPaired`() = runTest {
        val pairing = MasterPairing(masterInstallId = "m1", clientId = "c1", masterSecretEnc = "enc")
        whenever(repository.currentPairing()).thenReturn(pairing)

        val state = createViewModel().state.value

        assertThat(state).isInstanceOf(PairWithMasterViewModel.UiState.AlreadyPaired::class.java)
        assertThat((state as PairWithMasterViewModel.UiState.AlreadyPaired).pairing).isEqualTo(pairing)
    }

    @Test
    fun `onPinEntered from PinEntry moves to Fetching synchronously`() = runTest {
        val sut = createViewModel()

        sut.onPinEntered("12345678")

        // The launch body (fetcher.findOfferForPin) is parked on the test dispatcher; only the
        // synchronous transition to Fetching is observable here.
        assertThat(sut.state.value).isEqualTo(PairWithMasterViewModel.UiState.Fetching)
    }

    @Test
    fun `onPinEntered is ignored when not in PinEntry`() = runTest {
        val pairing = MasterPairing(masterInstallId = "m1", clientId = "c1", masterSecretEnc = "enc")
        whenever(repository.currentPairing()).thenReturn(pairing)
        val sut = createViewModel()

        sut.onPinEntered("12345678")

        assertThat(sut.state.value).isInstanceOf(PairWithMasterViewModel.UiState.AlreadyPaired::class.java)
    }

    @Test
    fun `resumePinEntry returns to PinEntry`() = runTest {
        val sut = createViewModel()
        sut.onPinEntered("12345678") // -> Fetching

        sut.resumePinEntry()

        assertThat(sut.state.value).isEqualTo(PairWithMasterViewModel.UiState.PinEntry)
    }

    @Test
    fun `cancelConfirmation from non-Confirming state is a no-op`() = runTest {
        val sut = createViewModel() // PinEntry

        sut.cancelConfirmation()

        assertThat(sut.state.value).isEqualTo(PairWithMasterViewModel.UiState.PinEntry)
    }

    @Test
    fun `unpair wipes the pairing and returns to PinEntry`() = runTest {
        val pairing = MasterPairing(masterInstallId = "m1", clientId = "c1", masterSecretEnc = "enc")
        whenever(repository.currentPairing()).thenReturn(pairing)
        val sut = createViewModel() // AlreadyPaired

        sut.unpair()
        // unpair() launches now: repository.unpair() takes the mutex, so it cannot run inline.
        advanceUntilIdle()

        verify(repository).unpair()
        assertThat(sut.state.value).isEqualTo(PairWithMasterViewModel.UiState.PinEntry)
    }
}

package app.aaps.plugins.eversense.pairing

import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.source.CgmAttention
import app.aaps.core.ui.compose.pump.ScanProblem
import app.aaps.plugins.eversense.pairing.EversensePairingViewModel.Step
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The pairing wizard's steps, on a fake driver: what moves it on, what keeps it where it is, and what it stores. */
@OptIn(ExperimentalCoroutinesApi::class)
class EversensePairingViewModelTest {

    /** A driver whose answers the test decides. */
    private class FakePairing : EversensePairing {

        override val account = MutableStateFlow<EversenseAccount?>(null)
        override val withoutSignIn = MutableStateFlow(false)
        override val transmitter = MutableStateFlow<String?>(null)
        override val attention = MutableStateFlow<CgmAttention?>(CgmAttention.NOT_PAIRED)
        var signInAnswer = SignInResult.OK
        val signIns = mutableListOf<Triple<String, String, EversenseModel>>()
        var pairAnswer = CompletableDeferred(PairResult.READY)
        val paired = mutableListOf<ScannedDevice>()
        val scanResults = MutableSharedFlow<List<ScannedDevice>>(replay = 1)
        var signedOut = false

        override suspend fun signIn(username: String, password: String, model: EversenseModel): SignInResult {
            signIns += Triple(username, password, model)
            if (signInAnswer == SignInResult.OK) account.value = EversenseAccount(username, model)
            return signInAnswer
        }

        override fun signOut() {
            signedOut = true
            account.value = null
        }

        override fun useWithoutSignIn() {
            account.value = null
            withoutSignIn.value = true
        }

        /** A scan that ends by itself, as the real one does after ten seconds; null: the running [scanResults] */
        var endingScan: Flow<List<ScannedDevice>>? = null
        var problem: ScanProblem? = null
        override fun scan(): Flow<List<ScannedDevice>> = endingScan ?: scanResults
        override fun scanProblem(): ScanProblem? = problem
        override suspend fun pair(transmitter: ScannedDevice): PairResult {
            paired += transmitter
            return pairAnswer.await()
        }

        override fun unpair() {}
    }

    private val pairing = FakePairing()
    private lateinit var viewModel: EversensePairingViewModel
    private val transmitter = ScannedDevice(name = "T123456", address = "00:11:22:33:44:55")

    @BeforeEach fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = EversensePairingViewModel(pairing)
    }

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private val state get() = viewModel.state.value

    private fun fillAccount(model: EversenseModel = EversenseModel.E365_USA) {
        viewModel.setModel(model)
        viewModel.setUsername(" user@example.com ")
        viewModel.setPassword("secret")
    }

    @Test fun `a new pairing goes through every step`() {
        viewModel.start(signInOnly = false)
        assertThat(state.step).isEqualTo(Step.BEFORE)

        viewModel.next()
        assertThat(state.step).isEqualTo(Step.ACCOUNT)

        fillAccount()
        viewModel.signIn()
        assertThat(pairing.signIns.single()).isEqualTo(Triple("user@example.com", "secret", EversenseModel.E365_USA))
        assertThat(state.step).isEqualTo(Step.TRANSMITTER)
        assertThat(state.password).isEmpty()   // not kept after the sign-in

        viewModel.startScan()
        pairing.scanResults.tryEmit(listOf(transmitter))
        assertThat(state.devices).containsExactly(transmitter)

        viewModel.select(transmitter)
        assertThat(pairing.paired.single()).isEqualTo(transmitter)
        assertThat(state.step).isEqualTo(Step.DONE)

        viewModel.finish()
        assertThat(state.finished).isTrue()
    }

    /** A scan that ends with nothing says so, and why when Bluetooth is off; "scan again" starts a new one. */
    @Test fun `an ended scan with nothing found says why and can run again`() {
        pairing.endingScan = flowOf(emptyList())
        pairing.problem = ScanProblem.BLUETOOTH_OFF

        viewModel.startScan()
        assertThat(state.scanning).isFalse()
        assertThat(state.scanProblem).isEqualTo(ScanProblem.BLUETOOTH_OFF)

        pairing.endingScan = null
        viewModel.startScan()
        assertThat(state.scanning).isTrue()
        assertThat(state.scanProblem).isNull()
    }

    @Test fun `a scan that found a transmitter names no problem`() {
        pairing.endingScan = flowOf(listOf(transmitter))
        pairing.problem = ScanProblem.BLUETOOTH_OFF

        viewModel.startScan()

        assertThat(state.scanning).isFalse()
        assertThat(state.devices).containsExactly(transmitter)
        assertThat(state.scanProblem).isNull()
    }

    /** Only the model, a username and a password make a sign-in possible: the model decides the server. */
    @Test fun `sign in needs the model and the login`() {
        viewModel.start(signInOnly = false)
        viewModel.next()
        viewModel.setUsername("user")
        viewModel.setPassword("secret")
        assertThat(state.canSignIn).isFalse()
        viewModel.signIn()
        assertThat(pairing.signIns).isEmpty()

        viewModel.setModel(EversenseModel.E3)
        assertThat(state.canSignIn).isTrue()
    }

    /** An E3 links without a login: the account step can be passed without one. A 365 cannot, it registers online. */
    @Test fun `an E3 can go on without signing in`() {
        viewModel.start(signInOnly = false)
        viewModel.next()
        viewModel.setModel(EversenseModel.E365_USA)
        assertThat(state.canGoOnWithoutSignIn).isFalse()
        viewModel.continueWithoutSignIn()
        assertThat(state.step).isEqualTo(Step.ACCOUNT)
        assertThat(pairing.withoutSignIn.value).isFalse()

        viewModel.setModel(EversenseModel.E3)
        viewModel.continueWithoutSignIn()

        assertThat(pairing.withoutSignIn.value).isTrue()
        assertThat(pairing.signIns).isEmpty()
        assertThat(state.step).isEqualTo(Step.TRANSMITTER)
    }

    /** Opened again later, the wizard remembers that the E3 is used without a login. */
    @Test fun `an E3 used without a login is offered again as E3`() {
        pairing.withoutSignIn.value = true
        viewModel.start(signInOnly = true)
        assertThat(state.model).isEqualTo(EversenseModel.E3)
    }

    /** A refused login keeps the step, says why, and keeps the typed values to correct them. */
    @Test fun `a refused sign-in stays on the account step`() {
        pairing.signInAnswer = SignInResult.REFUSED
        viewModel.start(signInOnly = false)
        viewModel.next()
        fillAccount()

        viewModel.signIn()

        assertThat(state.step).isEqualTo(Step.ACCOUNT)
        assertThat(state.signInError).isEqualTo(SignInResult.REFUSED)
        assertThat(state.password).isEqualTo("secret")

        viewModel.setPassword("corrected")
        assertThat(state.signInError).isNull()
    }

    /** Sign in again, from the status tab after the server refused the stored login: the wizard ends after it. */
    @Test fun `signing in again ends the wizard after the account step`() {
        viewModel.start(signInOnly = true)
        assertThat(state.step).isEqualTo(Step.ACCOUNT)
        fillAccount()

        viewModel.signIn()

        assertThat(state.finished).isTrue()
        assertThat(pairing.paired).isEmpty()
    }

    /** A stored login is offered: continue with it, or sign out and enter another one. */
    @Test fun `a stored login can be kept or signed out`() {
        pairing.account.value = EversenseAccount("user", EversenseModel.E3)
        viewModel.start(signInOnly = false)
        assertThat(state.model).isEqualTo(EversenseModel.E3)
        viewModel.next()

        viewModel.continueSignedIn()
        assertThat(state.step).isEqualTo(Step.TRANSMITTER)
        assertThat(pairing.signIns).isEmpty()

        viewModel.back()
        viewModel.signOut()
        assertThat(pairing.signedOut).isTrue()
        assertThat(viewModel.account.value).isNull()
    }

    /** The transmitter did not answer: the step says why, and Retry tries the same transmitter again. */
    @Test fun `a transmitter that does not answer can be tried again`() {
        pairing.account.value = EversenseAccount("user", EversenseModel.E3)
        pairing.pairAnswer = CompletableDeferred(PairResult.TIMED_OUT)
        viewModel.start(signInOnly = false)
        viewModel.next()
        viewModel.continueSignedIn()
        viewModel.select(transmitter)
        assertThat(state.step).isEqualTo(Step.CONNECT)
        assertThat(state.pairResult).isEqualTo(PairResult.TIMED_OUT)

        pairing.pairAnswer = CompletableDeferred(PairResult.READY)
        viewModel.retry()

        assertThat(pairing.paired).containsExactly(transmitter, transmitter).inOrder()
        assertThat(state.step).isEqualTo(Step.DONE)
    }

    /** While it waits for the transmitter, the wizard cannot be left: the link is being set up. */
    @Test fun `back does nothing while connecting`() {
        pairing.account.value = EversenseAccount("user", EversenseModel.E3)
        pairing.pairAnswer = CompletableDeferred()   // never answers in this test
        viewModel.start(signInOnly = false)
        viewModel.next()
        viewModel.continueSignedIn()
        viewModel.select(transmitter)
        assertThat(state.busy).isTrue()

        viewModel.back()

        assertThat(state.step).isEqualTo(Step.CONNECT)
        assertThat(state.finished).isFalse()
    }

    /** A new run starts clean: the "finished" of the last run must not close the wizard at once. */
    @Test fun `start clears the last run`() {
        viewModel.start(signInOnly = false)
        viewModel.finish()
        viewModel.start(signInOnly = false)
        assertThat(state.finished).isFalse()
        assertThat(state.step).isEqualTo(Step.BEFORE)
    }
}

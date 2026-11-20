package app.aaps.plugins.eversense.pairing

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Eversense pairing wizard, as a pump's: before you start, the account, the transmitter, the connection. Opened
 * at the account step alone to sign in again after the server refused the stored login.
 */
// Registers itself: @ViewModelKey infers the key from the class. Unscoped, so each wizard gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class EversensePairingViewModel(private val pairing: EversensePairing) : ViewModel() {

    enum class Step { BEFORE, ACCOUNT, TRANSMITTER, CONNECT, DONE }

    /**
     * @param signInOnly opened to sign in again: the wizard ends after the account step
     * @param model the Eversense chosen for the sign-in, which decides the server
     * @param finished the wizard is over and its screen should close
     */
    data class UiState(
        val step: Step = Step.BEFORE,
        val signInOnly: Boolean = false,
        val username: String = "",
        val password: String = "",
        val model: EversenseModel? = null,
        val busy: Boolean = false,
        val signInError: SignInResult? = null,
        val devices: List<ScannedDevice> = emptyList(),
        val transmitter: ScannedDevice? = null,
        val pairResult: PairResult? = null,
        val finished: Boolean = false
    ) {

        val canSignIn: Boolean get() = username.isNotBlank() && password.isNotEmpty() && model != null && !busy
    }

    /** The stored login: the account step offers to continue with it, or to sign out. */
    val account: StateFlow<EversenseAccount?> = pairing.account

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    /** A new pairing from the first step, or only a new sign-in (after the server refused the login). */
    fun start(signInOnly: Boolean) {
        stopScan()
        _state.value = UiState(
            step = if (signInOnly) Step.ACCOUNT else Step.BEFORE,
            signInOnly = signInOnly,
            model = account.value?.model
        )
    }

    fun next() = _state.update { if (it.step == Step.BEFORE) it.copy(step = Step.ACCOUNT) else it }

    /** One step back, or out of the wizard from its first step. Not while it waits for the transmitter or the server. */
    fun back() {
        val state = _state.value
        if (state.busy) return
        when (state.step) {
            Step.BEFORE, Step.DONE -> finish()
            Step.ACCOUNT           -> if (state.signInOnly) finish() else _state.update { it.copy(step = Step.BEFORE) }
            Step.TRANSMITTER       -> {
                stopScan()
                _state.update { it.copy(step = Step.ACCOUNT) }
            }

            Step.CONNECT           -> _state.update { it.copy(step = Step.TRANSMITTER, pairResult = null) }
        }
    }

    fun finish() {
        stopScan()
        _state.update { it.copy(finished = true) }
    }

    fun setUsername(value: String) = _state.update { it.copy(username = value, signInError = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, signInError = null) }
    fun setModel(value: EversenseModel) = _state.update { it.copy(model = value, signInError = null) }

    /** Checks the login with the server; only an accepted one is stored. The password is not kept here after it. */
    fun signIn() {
        val state = _state.value
        val model = state.model ?: return
        if (!state.canSignIn) return
        _state.update { it.copy(busy = true, signInError = null) }
        viewModelScope.launch {
            val result = pairing.signIn(state.username.trim(), state.password, model)
            _state.update {
                when {
                    result != SignInResult.OK -> it.copy(busy = false, signInError = result)
                    it.signInOnly             -> it.copy(busy = false, password = "", finished = true)
                    else                      -> it.copy(busy = false, password = "", step = Step.TRANSMITTER)
                }
            }
        }
    }

    /** Goes on with the stored login. */
    fun continueSignedIn() {
        if (account.value == null) return
        _state.update { if (it.signInOnly) it.copy(finished = true) else it.copy(step = Step.TRANSMITTER) }
    }

    /** Forgets the stored login, so another one can be entered. */
    fun signOut() {
        pairing.signOut()
        _state.update { it.copy(username = "", password = "", signInError = null) }
    }

    fun startScan() {
        stopScan()
        _state.update { it.copy(devices = emptyList()) }
        scanJob = viewModelScope.launch { pairing.scan().collect { found -> _state.update { it.copy(devices = found) } } }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun select(transmitter: ScannedDevice) {
        stopScan()
        _state.update { it.copy(step = Step.CONNECT, transmitter = transmitter) }
        pair(transmitter)
    }

    /** Tries the same transmitter again after it did not answer. */
    fun retry() {
        _state.value.transmitter?.let { pair(it) }
    }

    private fun pair(transmitter: ScannedDevice) {
        _state.update { it.copy(busy = true, pairResult = null) }
        viewModelScope.launch {
            val result = pairing.pair(transmitter)
            _state.update {
                if (result == PairResult.READY) it.copy(busy = false, step = Step.DONE)
                else it.copy(busy = false, pairResult = result)
            }
        }
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}

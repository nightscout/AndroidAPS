package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientControlMessage
import app.aaps.core.nssdk.localmodel.clientcontrol.MasterPairing
import app.aaps.core.nssdk.localmodel.clientcontrol.PairingPayload
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlPublisher
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientPairingRepository
import app.aaps.plugins.sync.nsclientV3.clientcontrol.PairingOfferFetcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@Stable
class PairWithMasterViewModel @Inject constructor(
    private val repository: ClientPairingRepository,
    private val publisher: ClientControlPublisher,
    private val fetcher: PairingOfferFetcher,
    private val dateUtil: DateUtil
) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(initialState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private fun initialState(): UiState =
        repository.currentPairing()?.let { UiState.AlreadyPaired(it) } ?: UiState.PinEntry

    /**
     * User submitted a PIN from the entry screen. Looks up matching offers on NS, attempts to
     * unwrap each with the typed PIN, and advances to [UiState.Confirming] on first success.
     *
     * Idempotent: ignored unless the screen is in [UiState.PinEntry] (re-tap on a slow network).
     * Distinguishes WrongPin / OfferExpired / NetworkUnavailable so the UI can offer a useful
     * retry path instead of a generic failure.
     */
    fun onPinEntered(pin: String) {
        if (_state.value !is UiState.PinEntry) return
        _state.value = UiState.Fetching
        viewModelScope.launch {
            when (val result = fetcher.findOfferForPin(pin)) {
                is PairingOfferFetcher.Result.Success   -> {
                    val payload = result.payload
                    if (payload.expiresAt > 0L && payload.expiresAt < dateUtil.now())
                        _state.value = UiState.Error(ErrorReason.OfferExpired)
                    else
                        _state.value = UiState.Confirming(payload)
                }

                PairingOfferFetcher.Result.NoMatch      -> _state.value = UiState.Error(ErrorReason.WrongPin)
                // Multiple offers decrypted with the same PIN — refuse to pick one; the user must
                // ask the master to regenerate. Pairing to the first one would be a silent attack.
                PairingOfferFetcher.Result.Ambiguous    -> _state.value = UiState.Error(ErrorReason.AmbiguousPin)
                PairingOfferFetcher.Result.NotAvailable -> _state.value = UiState.Error(ErrorReason.NetworkUnavailable)
            }
        }
    }

    /**
     * User confirmed the pairing. Persists locally, then publishes the signed hello
     * to NS in [UiState.Sending] (holds the screen alive — `viewModelScope` cancels
     * on pop, so the HTTP call must finish before we advance to Success).
     *
     * **Design decision — advance to Success even if the Hello upload fails:** the local
     * pairing has already been persisted and is fully valid; the only consequence of a
     * failed Hello is that the *master* stays in Pending until a future retry republishes
     * it. Surfacing a hard error here would block the user despite them holding a working
     * pairing, so we deliberately swallow the upload outcome and move on to Success.
     */
    fun confirmPair() {
        val payload = (_state.value as? UiState.Confirming)?.payload ?: return
        repository.pair(payload, dateUtil.now())
        _state.value = UiState.Sending
        viewModelScope.launch {
            publisher.publish(ClientControlMessage.Hello())
            _state.value = UiState.Success
        }
    }

    /** User cancelled the confirmation dialog. */
    fun cancelConfirmation() {
        if (_state.value is UiState.Confirming) _state.value = UiState.PinEntry
    }

    /** User taps "Try again" on an error state. */
    fun resumePinEntry() {
        _state.value = UiState.PinEntry
    }

    /** Wipes the existing pairing and returns the user to the entry screen. */
    fun unpair() {
        repository.unpair()
        _state.value = UiState.PinEntry
    }

    sealed class UiState {
        data object PinEntry : UiState()
        data object Fetching : UiState()
        data class Confirming(val payload: PairingPayload) : UiState()
        data object Sending : UiState()
        data object Success : UiState()
        data class Error(val reason: ErrorReason) : UiState()
        data class AlreadyPaired(val pairing: MasterPairing) : UiState()
    }

    enum class ErrorReason { WrongPin, AmbiguousPin, OfferExpired, NetworkUnavailable }
}

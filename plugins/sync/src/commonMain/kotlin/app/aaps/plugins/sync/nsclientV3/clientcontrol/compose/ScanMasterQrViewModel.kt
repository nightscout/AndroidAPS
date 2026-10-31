package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientSetupCode
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientPairingRepository
import app.aaps.plugins.sync.nsclientV3.clientcontrol.MasterDocVerifier
import app.aaps.plugins.sync.nsclientV3.clientcontrol.takeInstanceNameFromMaster
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The client reads the master's set-up QR code ([ClientSetupCode]): it sets up Nightscout with the client's own token,
 * and when the code says so, pairs the phone too.
 */
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class ScanMasterQrViewModel(
    private val pairingRepository: ClientPairingRepository,
    private val preferences: Preferences,
    private val configBuilder: ConfigBuilder,
    private val nsClientV3Plugin: () -> NSClientV3Plugin,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val masterDocVerifier: MasterDocVerifier
) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.Scanning)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** The text of a QR code in front of the camera. The camera reports the same code many times; only the first counts. */
    fun onScanned(text: String) {
        if (_state.value != UiState.Scanning) return
        _state.value = when (val decoded = ClientSetupCode.decode(text)) {
            ClientSetupCode.Decoded.NotACode     -> UiState.Error(ErrorReason.NotACode)
            ClientSetupCode.Decoded.OtherVersion -> UiState.Error(ErrorReason.OtherVersion)
            is ClientSetupCode.Decoded.Ok        -> {
                val pairing = decoded.code.pairing
                when {
                    pairing != null && pairing.expiresAt < dateUtil.now() -> UiState.Error(ErrorReason.Expired)
                    // Like the PIN: a second pairing would silently replace the first. Unpair first.
                    pairing != null && pairingRepository.isPaired()       -> UiState.Error(ErrorReason.AlreadyPaired)
                    else                                                  -> UiState.Confirming(decoded.code)
                }
            }
        }
    }

    /**
     * Uses the code. The pairing is stored first, then the Nightscout settings change, then NSClient is switched on if
     * it is not: the connection that starts is the first that can send the hello, and it does, because a new
     * pairing's secret is still to be replaced (`ClientPairingRepository.needsKeyExchange`).
     */
    fun confirm() {
        val code = (_state.value as? UiState.Confirming)?.code ?: return
        _state.value = UiState.Applying
        viewModelScope.launch {
            val pairing = code.pairing
            if (pairing != null && !pairingRepository.pair(pairing, dateUtil.now())) {
                _state.value = UiState.Error(ErrorReason.SecretStoreFailed)
                return@launch
            }
            preferences.put(StringKey.NsClientUrl, code.url)
            // Kept for the address just written, before the connection to it reads the first settings doc.
            code.signingKey?.let { masterDocVerifier.keepKey(it) }
            preferences.put(StringKey.NsClientAccessToken, code.token)
            // A client talks to its master over the websocket.
            preferences.put(BooleanKey.NsClient3UseWs, true)
            preferences.takeInstanceNameFromMaster(code.name)
            // Switched on last, so it starts with the settings above. Switched on first, a fresh client made its
            // Nightscout client with no address at all and sent the hello to "https://api/...".
            val plugin = nsClientV3Plugin()
            if (!plugin.isEnabled()) configBuilder.performPluginSwitch(plugin, true, PluginType.SYNC)
            aapsLogger.info(LTag.NSCLIENT, "ClientControl: set up from the master's QR code, paired=${pairing != null}")
            _state.value = UiState.Done(paired = pairing != null)
        }
    }

    /** Back to the camera, after an error or a "no" on the confirmation. */
    fun scanAgain() {
        _state.value = UiState.Scanning
    }

    sealed interface UiState {
        data object Scanning : UiState

        /** [code] is SENSITIVE (the token, maybe the secret); its toString hides them. */
        data class Confirming(val code: ClientSetupCode) : UiState
        data object Applying : UiState
        data class Done(val paired: Boolean) : UiState
        data class Error(val reason: ErrorReason) : UiState
    }

    enum class ErrorReason { NotACode, OtherVersion, Expired, AlreadyPaired, SecretStoreFailed }
}

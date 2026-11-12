package app.aaps.pump.omnipod.omnipod5.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5RegistrationData
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.security.SecureO5RegistrationStorage
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.omnipod5.keys.O5StringNonPreferenceKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

internal fun formatControllerId(controllerId: Long): String = String.format(Locale.US, "0x%08X", controllerId)

/** One row of the currently installed certificates list shown in the import screen. */
data class InstalledCredentialRow(
    val controllerId: Long,
    val source: O5RegistrationData.O5RegistrationSource
)

/** Result of the last certificate store action, so the screen can show a message. */
sealed class ImportResult {
    object None : ImportResult()
    object RemoveBlocked : ImportResult()
    data class Success(val controllerId: Long) : ImportResult()
    data class Failure(val reason: String) : ImportResult()
}

/**
 * Drives a settings screen for importing an Omnipod 5 certificate and viewing/removing
 * already-installed certificates. Accepts either format, auto-detected from the pasted
 * text:
 * - a `.o5keypair`-shaped JSON object (as produced by OmnipodKit's own `toJSON()` on
 *   iOS - `controllerId`/`privateKey`/`publicKey`/`intermediateCA`/`tlsCertificate`,
 *   keys hex-encoded and certs base64-encoded) - see [O5RegistrationData.fromJsonMap]
 * - the packed `"controllerId|priv|pub|ica|tls"` string format - see
 *   [O5RegistrationData.installPacked]
 *
 * Deliberately has no dosing-related functionality whatsoever - this only manages which
 * certificates [O5RegistrationData] knows about, nothing about pairing, connection, or
 * pod control.
 */
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class O5CredentialImportViewModel @Inject constructor(
    private val secureO5RegistrationStorage: SecureO5RegistrationStorage,
    private val podStateManager: O5PodStateManager,
    private val preferences: Preferences,
    private val rh: ResourceHelper
) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    private val _importResult = MutableStateFlow<ImportResult>(ImportResult.None)
    val importResult: StateFlow<ImportResult> = _importResult

    private val _installedCredentials = MutableStateFlow<List<InstalledCredentialRow>>(emptyList())
    val installedCredentials: StateFlow<List<InstalledCredentialRow>> = _installedCredentials

    // PodState preference updates signal that persisted pod state changed; re-read the
    // active-pod state from podStateManager because it owns the parsed state.
    val canRemoveCertificate: StateFlow<Boolean> = preferences.observe(O5StringNonPreferenceKey.PodState)
        .map { !hasActivePod() }
        .onEach { canRemove ->
            if (canRemove && _importResult.value == ImportResult.RemoveBlocked) {
                _importResult.value = ImportResult.None
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, !hasActivePod())

    init {
        refreshInstalledCredentials()
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
        if (_importResult.value != ImportResult.None) {
            _importResult.value = ImportResult.None
        }
    }

    /**
     * Attempts to parse and install [inputText]'s current value, auto-detecting whether
     * it's a `.o5keypair`-shaped JSON object or a packed certificate string (see class doc).
     * On success, also persists it (encrypted) so it survives app restarts, and clears the
     * input field. On failure, leaves the input as-is so the user can correct it.
     */
    fun importCurrentInput() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) {
            _importResult.value = ImportResult.Failure(rh.gs(R.string.omnipod_5_certificate_import_paste_first))
            return
        }

        importText(text)
    }

    /**
     * Imports a certificate JSON/packed string received from the pairing web page (via the
     * WebView message bridge). Runs the same parse/install path as a manual paste and
     * returns whether a certificate was successfully installed.
     */
    fun importFromWebMessage(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _importResult.value = ImportResult.Failure(rh.gs(R.string.omnipod_5_certificate_import_empty))
            return false
        }
        importText(trimmed)
        return _importResult.value is ImportResult.Success
    }

    fun importError(throwable: Throwable) {
        _importResult.value = ImportResult.Failure(throwable.message ?: "Import failed unexpectedly")
        return
    }

    private fun importText(text: String) {
        val controllerId = if (text.startsWith("{")) importJsonCredential(text) else importPackedCredential(text)
        if (controllerId == null) {
            _importResult.value = ImportResult.Failure(
                rh.gs(R.string.omnipod_5_certificate_import_parse_error)
            )
            return
        }

        val installed = O5RegistrationData.get(controllerId)
        if (installed == null) {
            _importResult.value = ImportResult.Failure("Import failed unexpectedly")
            return
        }

        secureO5RegistrationStorage.persistEntry(installed, O5RegistrationData.O5RegistrationSource.IMPORTED)
        _importResult.value = ImportResult.Success(controllerId)
        _inputText.value = ""
        refreshInstalledCredentials()
    }

    /**
     * Parses [text] as the `.o5keypair`-shaped JSON object OmnipodKit's own `toJSON()`
     * produces on iOS and installs it if valid. Returns the resulting controllerId, or
     * null if the text wasn't valid JSON or was missing a required field.
     */
    private fun importJsonCredential(text: String): Long? {
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return null
        }
        val map = REQUIRED_JSON_KEYS.associateWith { key -> if (json.has(key) && !json.isNull(key)) json.optString(key) else null }
        val data = O5RegistrationData.fromJsonMap(map) ?: return null
        O5RegistrationData.install(data, O5RegistrationData.O5RegistrationSource.IMPORTED)
        return data.controllerId
    }

    /**
     * Parses [text] as a packed `"controllerId|priv|pub|ica|tls"` string and installs it
     * if valid (see [O5RegistrationData.installPacked]). Returns the resulting
     * controllerId, or null if parsing/installation failed.
     */
    private fun importPackedCredential(text: String): Long? {
        val controllerId = parseControllerIdFromPacked(text)
        val ok = O5RegistrationData.installPacked(text)
        return controllerId.takeIf { ok }
    }

    /** Removes a certificate from both the in-memory registry and persisted storage. */
    fun removeCredential(controllerId: Long) {
        if (hasActivePod()) {
            _importResult.value = ImportResult.RemoveBlocked
            return
        }
        O5RegistrationData.remove(controllerId)
        secureO5RegistrationStorage.removeEntry(controllerId)
        _importResult.value = ImportResult.None
        refreshInstalledCredentials()
    }

    private fun hasActivePod(): Boolean = podStateManager.activationProgress == ActivationProgress.COMPLETED

    private fun refreshInstalledCredentials() {
        _installedCredentials.value = O5RegistrationData.allValues.mapNotNull { data ->
            O5RegistrationData.source(data.controllerId)?.let { source ->
                InstalledCredentialRow(data.controllerId, source)
            }
        }
    }

    /**
     * Pulls just the controllerId out of a packed string, without fully parsing/validating
     * it - used so a failed [O5RegistrationData.installPacked] call can still be attributed
     * to a specific controllerId if the string was at least well-formed enough to read one.
     * Returns null for anything that doesn't even have a parseable leading controllerId field.
     */
    private fun parseControllerIdFromPacked(packed: String): Long? =
        packed.substringBefore("|").toLongOrNull()

    private companion object {
        /** Matches the keys OmnipodKit's `O5RegistrationData.toJSON()` produces on iOS. */
        val REQUIRED_JSON_KEYS = listOf("controllerId", "privateKey", "publicKey", "intermediateCA", "tlsCertificate")
    }
}

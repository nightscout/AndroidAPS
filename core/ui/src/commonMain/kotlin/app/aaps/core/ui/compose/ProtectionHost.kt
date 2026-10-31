package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.protection.AuthorizationResult
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.protection.ProtectionType
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.dialogs.QueryPasswordDialog
import app.aaps.core.ui.compose.dialogs.UnifiedAuthDialog

/**
 * Renders the password, PIN and biometric prompts that `ProtectionCheck` asks for.
 *
 * `ProtectionCheck` publishes a request and then waits for a host to answer it. That makes placing
 * this host a correctness requirement, not a decoration: a screen that asks to be protected on a
 * platform with no host would publish a request nobody renders, and the caller would wait forever.
 * It is called from `AapsAppRoot`, so every platform that shows the shared UI has it.
 *
 * ## The biometric prompt is a lambda, and used to carry an Android type
 *
 * The prompt itself is the only platform-specific part - `androidx.biometric` on Android,
 * `LAContext` on iOS, and nothing at all on a desktop. It was already a lambda, but its first
 * parameter was a `FragmentActivity`, which is what kept this whole file on Android. The activity
 * was only ever used to root the prompt, and the Android caller has it anyway, so it now captures it
 * instead of being handed it.
 *
 * **A platform lambda must call exactly one of its callbacks.** Nothing here times out: a lambda that
 * returns without answering leaves the caller waiting, which is the failure this host exists to
 * prevent.
 *
 * @param checkPassword verifies an entered password against the stored hash
 * @param showBiometric prompt for a request that asked for biometric specifically
 * @param showBiometricSimple prompt that may fall back to a password, for a hierarchical request
 */
@Composable
fun ProtectionHost(
    protectionCheck: ProtectionCheck,
    preferences: Preferences,
    checkPassword: (password: String, hash: String) -> Boolean,
    showBiometric: (title: String, onGranted: () -> Unit, onCancelled: () -> Unit, onDenied: () -> Unit) -> Unit =
        { _, _, onCancelled, _ -> onCancelled() },
    showBiometricSimple: (title: String, onSuccess: () -> Unit, onFallback: () -> Unit, onCancel: () -> Unit) -> Unit =
        { _, _, onFallback, _ -> onFallback() }
) {
    // --- Hierarchical auth requests ---
    val authRequest by protectionCheck.pendingAuthRequest.collectAsStateWithLifecycle()

    authRequest?.let { req ->
        var showDialog by remember(req.id) { mutableStateOf(!req.hasBiometric) }
        // Read while still in composition: stringResource is @Composable, the biometric callback is not.
        val biometricTitle = stringResource(CoreUiStrings.biometric_title)

        if (req.hasBiometric && !showDialog) {
            LaunchedEffect(req.id) {
                showBiometricSimple(
                    biometricTitle,
                    {
                        // Biometric success -> grant the highest level that biometric covers
                        protectionCheck.completeAuthRequest(
                            req.id,
                            AuthorizationResult(req.biometricGrantsLevel, ProtectionResult.GRANTED)
                        )
                    },
                    // Fallback -> ask for a password or PIN instead. This is also what the default
                    // lambda does, so a platform with no biometrics asks for credentials rather than
                    // refusing the user outright.
                    { showDialog = true },
                    {
                        protectionCheck.completeAuthRequest(
                            req.id,
                            AuthorizationResult(null, ProtectionResult.CANCELLED)
                        )
                    }
                )
            }
        }

        if (showDialog) {
            // Biometric methods are handled above, so only credential ones are offered here.
            val credentialMethods = req.availableMethods.filter { it.type != ProtectionType.BIOMETRIC }
            UnifiedAuthDialog(
                methods = credentialMethods,
                checkPassword = checkPassword,
                onResult = { result -> protectionCheck.completeAuthRequest(req.id, result) }
            )
        }
    }

    // --- Single level requests ---
    val request by protectionCheck.pendingRequest.collectAsStateWithLifecycle()

    request?.let { req ->
        val reqTitle = stringResource(req.title)
        when (req.type) {
            ProtectionType.NONE            -> {
                LaunchedEffect(req.id) {
                    protectionCheck.completeRequest(req.id, ProtectionResult.GRANTED)
                }
            }

            ProtectionType.BIOMETRIC       -> {
                LaunchedEffect(req.id) {
                    // No fallback here: this request asked for biometric specifically. The default
                    // lambda cancels, so a platform without biometrics refuses rather than grants.
                    showBiometric(
                        reqTitle,
                        { protectionCheck.completeRequest(req.id, ProtectionResult.GRANTED) },
                        { protectionCheck.completeRequest(req.id, ProtectionResult.CANCELLED) },
                        { protectionCheck.completeRequest(req.id, ProtectionResult.DENIED) }
                    )
                }
            }

            ProtectionType.MASTER_PASSWORD -> {
                val storedHash = preferences.get(StringKey.ProtectionMasterPassword)
                QueryPasswordDialog(
                    title = reqTitle,
                    pinInput = false,
                    onConfirm = { enteredPassword ->
                        protectionCheck.completeRequest(req.id, resultFor(checkPassword(enteredPassword, storedHash)))
                    },
                    onCancel = { protectionCheck.completeRequest(req.id, ProtectionResult.CANCELLED) }
                )
            }

            ProtectionType.CUSTOM_PASSWORD -> {
                val passwordKey = when (req.protection) {
                    ProtectionCheck.Protection.PREFERENCES -> StringKey.ProtectionSettingsPassword
                    ProtectionCheck.Protection.APPLICATION -> StringKey.ProtectionApplicationPassword
                    ProtectionCheck.Protection.BOLUS       -> StringKey.ProtectionBolusPassword
                    else                                   -> return // should never reach here
                }
                val storedHash = preferences.get(passwordKey)
                QueryPasswordDialog(
                    title = reqTitle,
                    pinInput = false,
                    onConfirm = { enteredPassword ->
                        protectionCheck.completeRequest(req.id, resultFor(checkPassword(enteredPassword, storedHash)))
                    },
                    onCancel = { protectionCheck.completeRequest(req.id, ProtectionResult.CANCELLED) }
                )
            }

            ProtectionType.CUSTOM_PIN      -> {
                val pinKey = when (req.protection) {
                    ProtectionCheck.Protection.PREFERENCES -> StringKey.ProtectionSettingsPin
                    ProtectionCheck.Protection.APPLICATION -> StringKey.ProtectionApplicationPin
                    ProtectionCheck.Protection.BOLUS       -> StringKey.ProtectionBolusPin
                    else                                   -> return // should never reach here
                }
                val storedHash = preferences.get(pinKey)
                QueryPasswordDialog(
                    title = reqTitle,
                    pinInput = true,
                    onConfirm = { enteredPin ->
                        protectionCheck.completeRequest(req.id, resultFor(checkPassword(enteredPin, storedHash)))
                    },
                    onCancel = { protectionCheck.completeRequest(req.id, ProtectionResult.CANCELLED) }
                )
            }
        }
    }
}

/** Granted only on a match. Wrong credentials are denied, which is not the same as cancelled. */
private fun resultFor(matches: Boolean): ProtectionResult =
    if (matches) ProtectionResult.GRANTED else ProtectionResult.DENIED

package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.protection.AuthorizationResult
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.protection.ProtectionType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.dialogs.UnifiedAuthDialog
import kotlinx.coroutines.flow.first

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
 * @param showBiometricSimple biometric prompt that may fall back to the password dialog
 */
@Composable
fun ProtectionHost(
    protectionCheck: ProtectionCheck,
    checkPassword: (password: String, hash: String) -> Boolean,
    showBiometricSimple: (title: String, onSuccess: () -> Unit, onFallback: () -> Unit, onCancel: () -> Unit) -> Unit =
        { _, _, onFallback, _ -> onFallback() }
) {
    // A pending request's callback belongs to the screen that asked for it. When this host goes away
    // (the activity is destroyed: rotation, a theme or language change, or Android closing it in the
    // background) answer the request with CANCELLED. Otherwise the next host shows it again and, after
    // the password, the old callback runs against the dead screen - it navigated on a destroyed
    // NavController and crashed. The user just taps again on the new screen.
    DisposableEffect(protectionCheck) {
        onDispose {
            protectionCheck.pendingAuthRequest.value?.let {
                protectionCheck.completeAuthRequest(it.id, AuthorizationResult(null, ProtectionResult.CANCELLED))
            }
        }
    }

    val authRequest by protectionCheck.pendingAuthRequest.collectAsStateWithLifecycle()

    authRequest?.let { req ->
        var showDialog by remember(req.id) { mutableStateOf(!req.hasBiometric) }
        // Read while still in composition: stringResource is @Composable, the biometric callback is not.
        val biometricTitle = stringResource(CoreUiStrings.biometric_title)

        if (req.hasBiometric && !showDialog) {
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(req.id) {
                // A request can come while another activity is on top, for example the Dexcom permission
                // activity at app start. A prompt started then is dropped by the system, so wait.
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
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
}

package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.protection.AuthorizationResult
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.protection.ProtectionType
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.dialogs.QueryPasswordDialog
import app.aaps.core.ui.compose.dialogs.UnifiedAuthDialog

/**
 * Composable that hosts protection dialogs (password, PIN, biometric).
 *
 * Place this at the root of your Compose hierarchy (inside AapsTheme) to handle
 * protection requests triggered via [ProtectionCheck.requestProtection] and
 * [ProtectionCheck.requestAuthorization].
 *
 * @param protectionCheck The protection check instance
 * @param preferences User preferences for retrieving stored password hashes
 * @param checkPassword Function to verify password against stored hash: (enteredPassword, storedHash) -> Boolean
 * @param showBiometric Function to show biometric prompt: (activity, titleRes, onGranted, onCancelled, onDenied) -> Unit
 * @param showBiometricSimple Function to show biometric prompt with fallback: (activity, titleRes, onSuccess, onFallback, onCancel) -> Unit
 */
@Composable
fun ProtectionHost(
    protectionCheck: ProtectionCheck,
    preferences: Preferences,
    checkPassword: (password: String, hash: String) -> Boolean,
    showBiometric: (FragmentActivity, Int, () -> Unit, () -> Unit, () -> Unit) -> Unit,
    showBiometricSimple: (FragmentActivity, Int, () -> Unit, () -> Unit, () -> Unit) -> Unit = showBiometric
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // --- Handle hierarchical auth requests (new API) ---
    val authRequest by protectionCheck.pendingAuthRequest.collectAsStateWithLifecycle()

    authRequest?.let { req ->
        var showDialog by remember(req.id) { mutableStateOf(!req.hasBiometric) }

        if (req.hasBiometric && !showDialog) {
            LaunchedEffect(req.id) {
                if (activity != null) {
                    showBiometricSimple(
                        activity,
                        app.aaps.core.ui.R.string.biometric_title,
                        {
                            // Biometric success → grant highest level that uses biometric
                            protectionCheck.completeAuthRequest(
                                req.id,
                                AuthorizationResult(req.biometricGrantsLevel, ProtectionResult.GRANTED)
                            )
                        },
                        { showDialog = true },  // Fallback → show unified dialog
                        {
                            protectionCheck.completeAuthRequest(
                                req.id,
                                AuthorizationResult(null, ProtectionResult.CANCELLED)
                            )
                        }
                    )
                } else {
                    showDialog = true
                }
            }
        }

        if (showDialog) {
            // Filter out biometric methods — those are handled above
            val credentialMethods = req.availableMethods.filter { it.type != ProtectionType.BIOMETRIC }
            UnifiedAuthDialog(
                methods = credentialMethods,
                checkPassword = checkPassword,
                onResult = { result ->
                    protectionCheck.completeAuthRequest(req.id, result)
                }
            )
        }
    }

    // --- Handle legacy single-level requests (existing API) ---
    val request by protectionCheck.pendingRequest.collectAsStateWithLifecycle()

    request?.let { req ->
        when (req.type) {
            ProtectionType.NONE            -> {
                LaunchedEffect(req.id) {
                    protectionCheck.completeRequest(req.id, ProtectionResult.GRANTED)
                }
            }

            ProtectionType.BIOMETRIC       -> {
                LaunchedEffect(req.id) {
                    if (activity != null) {
                        showBiometric(
                            activity,
                            req.titleRes,
                            { protectionCheck.completeRequest(req.id, ProtectionResult.GRANTED) },
                            { protectionCheck.completeRequest(req.id, ProtectionResult.CANCELLED) },
                            { protectionCheck.completeRequest(req.id, ProtectionResult.DENIED) }
                        )
                    } else {
                        protectionCheck.completeRequest(req.id, ProtectionResult.CANCELLED)
                    }
                }
            }

            ProtectionType.MASTER_PASSWORD -> {
                val storedHash = preferences.get(StringKey.ProtectionMasterPassword)
                QueryPasswordDialog(
                    title = stringResource(req.titleRes),
                    pinInput = false,
                    onConfirm = { enteredPassword ->
                        if (checkPassword(enteredPassword, storedHash)) {
                            protectionCheck.completeRequest(req.id, ProtectionResult.GRANTED)
                        } else {
                            protectionCheck.completeRequest(req.id, ProtectionResult.DENIED)
                        }
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
                    title = stringResource(req.titleRes),
                    pinInput = false,
                    onConfirm = { enteredPassword ->
                        if (checkPassword(enteredPassword, storedHash)) {
                            protectionCheck.completeRequest(req.id, ProtectionResult.GRANTED)
                        } else {
                            protectionCheck.completeRequest(req.id, ProtectionResult.DENIED)
                        }
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
                    title = stringResource(req.titleRes),
                    pinInput = true,
                    onConfirm = { enteredPin ->
                        if (checkPassword(enteredPin, storedHash)) {
                            protectionCheck.completeRequest(req.id, ProtectionResult.GRANTED)
                        } else {
                            protectionCheck.completeRequest(req.id, ProtectionResult.DENIED)
                        }
                    },
                    onCancel = { protectionCheck.completeRequest(req.id, ProtectionResult.CANCELLED) }
                )
            }
        }
    }
}

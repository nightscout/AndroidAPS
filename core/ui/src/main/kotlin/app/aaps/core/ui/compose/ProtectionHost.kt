package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.protection.ProtectionType
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.dialogs.QueryPasswordDialog

/**
 * Composable that hosts protection dialogs (password, PIN, biometric).
 *
 * Place this at the root of your Compose hierarchy (inside AapsTheme) to handle
 * protection requests triggered via [ProtectionCheck.requestProtection].
 *
 * @param protectionCheck The protection check instance
 * @param preferences User preferences for retrieving stored password hashes
 * @param checkPassword Function to verify password against stored hash: (enteredPassword, storedHash) -> Boolean
 * @param showBiometric Function to show biometric prompt: (activity, titleRes, onGranted, onCancelled, onDenied) -> Unit
 */
@Composable
fun ProtectionHost(
    protectionCheck: ProtectionCheck,
    preferences: Preferences,
    checkPassword: (password: String, hash: String) -> Boolean,
    showBiometric: (FragmentActivity, Int, () -> Unit, () -> Unit, () -> Unit) -> Unit
) {
    val request by protectionCheck.pendingRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

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
                    ProtectionCheck.Protection.NONE        -> return // should never reach here
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
                    ProtectionCheck.Protection.NONE        -> return // should never reach here
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

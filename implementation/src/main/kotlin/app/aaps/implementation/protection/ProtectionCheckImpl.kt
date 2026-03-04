package app.aaps.implementation.protection

import androidx.annotation.UiThread
import androidx.fragment.app.FragmentActivity
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionRequest
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.protection.ProtectionType
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import dagger.Reusable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Reusable
class ProtectionCheckImpl @Inject constructor(
    private val preferences: Preferences,
    private val passwordCheck: PasswordCheck,
    private val dateUtil: DateUtil
) : ProtectionCheck {

    private var lastAuthorization = mutableListOf(0L, 0L, 0L)
    private var requestIdCounter = 0L
    private val _pendingRequest = MutableStateFlow<ProtectionRequest?>(null)
    override val pendingRequest: StateFlow<ProtectionRequest?> = _pendingRequest.asStateFlow()

    private val passwordsResourceIDs = listOf(
        StringKey.ProtectionSettingsPassword,
        StringKey.ProtectionApplicationPassword,
        StringKey.ProtectionBolusPassword
    )

    private val pinsResourceIDs = listOf(
        StringKey.ProtectionSettingsPin,
        StringKey.ProtectionApplicationPin,
        StringKey.ProtectionBolusPin
    )

    private val protectionTypeResourceIDs = listOf(
        IntKey.ProtectionTypeSettings,
        IntKey.ProtectionTypeApplication,
        IntKey.ProtectionTypeBolus
    )

    private val titlePassResourceIDs = listOf(
        app.aaps.core.ui.R.string.settings_password,
        app.aaps.core.ui.R.string.application_password,
        app.aaps.core.ui.R.string.bolus_password
    )

    private val titlePinResourceIDs = listOf(
        app.aaps.core.ui.R.string.settings_pin,
        app.aaps.core.ui.R.string.application_pin,
        app.aaps.core.ui.R.string.bolus_pin
    )

    override fun isLocked(protection: ProtectionCheck.Protection): Boolean {
        // No master password = no protection at all
        if (preferences.get(StringKey.ProtectionMasterPassword).isEmpty()) {
            return false
        }
        if (activeSession(protection)) {
            return false
        }
        return when (ProtectionType.entries[preferences.get(protectionTypeResourceIDs[protection.ordinal])]) {
            ProtectionType.NONE            -> false
            ProtectionType.BIOMETRIC       -> true
            ProtectionType.MASTER_PASSWORD -> true
            ProtectionType.CUSTOM_PASSWORD -> preferences.get(passwordsResourceIDs[protection.ordinal]).isNotEmpty()
            ProtectionType.CUSTOM_PIN      -> preferences.get(pinsResourceIDs[protection.ordinal]).isNotEmpty()
        }
    }

    override fun resetAuthorization() {
        lastAuthorization = mutableListOf(0L, 0L, 0L)
    }

    private fun activeSession(protection: ProtectionCheck.Protection): Boolean {
        val timeout = TimeUnit.SECONDS.toMillis(preferences.get(IntKey.ProtectionTimeout).toLong())
        if (timeout <= 0) return false // 0 = always ask
        val last = lastAuthorization[protection.ordinal]
        val diff = dateUtil.now() - last
        return diff < timeout
    }

    private fun onOk(protection: ProtectionCheck.Protection) {
        lastAuthorization[protection.ordinal] = dateUtil.now()
    }

    @UiThread
    override fun queryProtection(activity: FragmentActivity, protection: ProtectionCheck.Protection, ok: Runnable?, cancel: Runnable?, fail: Runnable?) {
        // No master password = no protection at all
        if (preferences.get(StringKey.ProtectionMasterPassword).isEmpty()) {
            ok?.run()
            return
        }
        if (activeSession(protection)) {
            onOk(protection)
            ok?.run()
            return
        }

        when (ProtectionType.entries[preferences.get(protectionTypeResourceIDs[protection.ordinal])]) {
            ProtectionType.NONE            ->
                ok?.run()

            ProtectionType.BIOMETRIC       ->
                BiometricCheck.biometricPrompt(activity, titlePassResourceIDs[protection.ordinal], { onOk(protection); ok?.run() }, cancel, fail, passwordCheck)

            ProtectionType.MASTER_PASSWORD ->
                passwordCheck.queryPassword(
                    activity,
                    app.aaps.core.keys.R.string.master_password,
                    StringKey.ProtectionMasterPassword,
                    { onOk(protection); ok?.run() },
                    { cancel?.run() },
                    { fail?.run() })

            ProtectionType.CUSTOM_PASSWORD ->
                passwordCheck.queryPassword(
                    activity,
                    titlePassResourceIDs[protection.ordinal],
                    passwordsResourceIDs[protection.ordinal],
                    { onOk(protection); ok?.run() },
                    { cancel?.run() },
                    { fail?.run() })

            ProtectionType.CUSTOM_PIN      ->
                passwordCheck.queryPassword(
                    activity,
                    titlePinResourceIDs[protection.ordinal],
                    pinsResourceIDs[protection.ordinal],
                    { onOk(protection); ok?.run() },
                    { cancel?.run() },
                    { fail?.run() },
                    true
                )
        }
    }

    override fun requestProtection(protection: ProtectionCheck.Protection, onResult: (ProtectionResult) -> Unit) {
        // No master password = no protection at all
        if (preferences.get(StringKey.ProtectionMasterPassword).isEmpty()) {
            onOk(protection)
            onResult(ProtectionResult.GRANTED)
            return
        }

        // Check active session
        if (activeSession(protection)) {
            onOk(protection)
            onResult(ProtectionResult.GRANTED)
            return
        }

        val type = ProtectionType.entries[preferences.get(protectionTypeResourceIDs[protection.ordinal])]

        // No protection configured
        if (type == ProtectionType.NONE) {
            onOk(protection)
            onResult(ProtectionResult.GRANTED)
            return
        }

        val titleRes = when (type) {
            ProtectionType.CUSTOM_PIN -> titlePinResourceIDs[protection.ordinal]
            else                      -> titlePassResourceIDs[protection.ordinal]
        }

        _pendingRequest.value = ProtectionRequest(
            id = ++requestIdCounter,
            protection = protection,
            type = type,
            titleRes = titleRes,
            onResult = { result ->
                if (result == ProtectionResult.GRANTED) onOk(protection)
                _pendingRequest.value = null
                onResult(result)
            }
        )
    }

    override fun completeRequest(requestId: Long, result: ProtectionResult) {
        _pendingRequest.value?.let { request ->
            if (request.id == requestId) {
                request.onResult(result)
            }
        }
    }
}

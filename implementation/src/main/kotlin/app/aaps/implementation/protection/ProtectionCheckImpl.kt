package app.aaps.implementation.protection

import androidx.annotation.UiThread
import androidx.fragment.app.FragmentActivity
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import dagger.Reusable
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Reusable
class ProtectionCheckImpl @Inject constructor(
    private val preferences: Preferences,
    private val passwordCheck: PasswordCheck,
    private val dateUtil: DateUtil
) : ProtectionCheck {

    private var lastAuthorization = mutableListOf(0L, 0L, 0L)

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
        if (activeSession(protection)) {
            return false
        }
        return when (ProtectionCheck.ProtectionType.entries[preferences.get(protectionTypeResourceIDs[protection.ordinal])]) {
            ProtectionCheck.ProtectionType.NONE            -> false
            ProtectionCheck.ProtectionType.BIOMETRIC       -> true
            ProtectionCheck.ProtectionType.MASTER_PASSWORD -> preferences.get(StringKey.ProtectionMasterPassword) != ""
            ProtectionCheck.ProtectionType.CUSTOM_PASSWORD -> preferences.get(passwordsResourceIDs[protection.ordinal]) != ""
            ProtectionCheck.ProtectionType.CUSTOM_PIN      -> preferences.get(pinsResourceIDs[protection.ordinal]) != ""
        }
    }

    override fun resetAuthorization() {
        lastAuthorization = mutableListOf(0L, 0L, 0L)
    }

    private fun activeSession(protection: ProtectionCheck.Protection): Boolean {
        var timeout = TimeUnit.SECONDS.toMillis(preferences.get(IntKey.ProtectionTimeout).toLong())
        // Default timeout to pass the resume check at start of an activity
        timeout = if (timeout < 1000) 1000 else timeout
        val last = lastAuthorization[protection.ordinal]
        val diff = dateUtil.now() - last
        return diff < timeout
    }

    private fun onOk(protection: ProtectionCheck.Protection) {
        lastAuthorization[protection.ordinal] = dateUtil.now()
    }

    @UiThread
    override fun queryProtection(activity: FragmentActivity, protection: ProtectionCheck.Protection, ok: Runnable?, cancel: Runnable?, fail: Runnable?) {
        if (activeSession(protection)) {
            onOk(protection)
            ok?.run()
            return
        }

        when (ProtectionCheck.ProtectionType.entries[preferences.get(protectionTypeResourceIDs[protection.ordinal])]) {
            ProtectionCheck.ProtectionType.NONE            ->
                ok?.run()

            ProtectionCheck.ProtectionType.BIOMETRIC       ->
                BiometricCheck.biometricPrompt(activity, titlePassResourceIDs[protection.ordinal], { onOk(protection); ok?.run() }, cancel, fail, passwordCheck)

            ProtectionCheck.ProtectionType.MASTER_PASSWORD ->
                passwordCheck.queryPassword(
                    activity,
                    app.aaps.core.ui.R.string.master_password,
                    StringKey.ProtectionMasterPassword,
                    { onOk(protection); ok?.run() },
                    { cancel?.run() },
                    { fail?.run() })

            ProtectionCheck.ProtectionType.CUSTOM_PASSWORD ->
                passwordCheck.queryPassword(
                    activity,
                    titlePassResourceIDs[protection.ordinal],
                    passwordsResourceIDs[protection.ordinal],
                    { onOk(protection); ok?.run() },
                    { cancel?.run() },
                    { fail?.run() })

            ProtectionCheck.ProtectionType.CUSTOM_PIN      ->
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
}

package info.nightscout.implementation.protection

import androidx.fragment.app.FragmentActivity
import dagger.Reusable
import info.nightscout.interfaces.protection.PasswordCheck
import info.nightscout.interfaces.protection.ProtectionCheck
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Reusable
class ProtectionCheckImpl @Inject constructor(
    private val sp: SP,
    private val passwordCheck: PasswordCheck,
    private val dateUtil: DateUtil
) : ProtectionCheck {

    private var lastAuthorization = mutableListOf(0L, 0L, 0L)

    private val passwordsResourceIDs = listOf(
        info.nightscout.core.utils.R.string.key_settings_password,
        info.nightscout.core.utils.R.string.key_application_password,
        info.nightscout.core.utils.R.string.key_bolus_password
    )

    private val pinsResourceIDs = listOf(
        info.nightscout.core.utils.R.string.key_settings_pin,
        info.nightscout.core.utils.R.string.key_application_pin,
        info.nightscout.core.utils.R.string.key_bolus_pin)

    private val protectionTypeResourceIDs = listOf(
        info.nightscout.core.utils.R.string.key_settings_protection,
        info.nightscout.core.utils.R.string.key_application_protection,
        info.nightscout.core.utils.R.string.key_bolus_protection)

    private val titlePassResourceIDs = listOf(
        info.nightscout.core.ui.R.string.settings_password,
        info.nightscout.core.ui.R.string.application_password,
        info.nightscout.core.ui.R.string.bolus_password)

    private val titlePinResourceIDs = listOf(
        info.nightscout.core.ui.R.string.settings_pin,
        info.nightscout.core.ui.R.string.application_pin,
        info.nightscout.core.ui.R.string.bolus_pin)

    override fun isLocked(protection: ProtectionCheck.Protection): Boolean {
        if (activeSession(protection)) {
            return false
        }
        return when (ProtectionCheck.ProtectionType.values()[sp.getInt(protectionTypeResourceIDs[protection.ordinal], ProtectionCheck.ProtectionType.NONE.ordinal)]) {
            ProtectionCheck.ProtectionType.NONE            -> false
            ProtectionCheck.ProtectionType.BIOMETRIC       -> true
            ProtectionCheck.ProtectionType.MASTER_PASSWORD -> sp.getString(info.nightscout.core.utils.R.string.key_master_password, "") != ""
            ProtectionCheck.ProtectionType.CUSTOM_PASSWORD -> sp.getString(passwordsResourceIDs[protection.ordinal], "") != ""
            ProtectionCheck.ProtectionType.CUSTOM_PIN      -> sp.getString(pinsResourceIDs[protection.ordinal], "") != ""
        }
    }

    override fun resetAuthorization() {
        lastAuthorization = mutableListOf(0L, 0L, 0L)
    }

    private fun activeSession(protection: ProtectionCheck.Protection): Boolean {
        var timeout = TimeUnit.SECONDS.toMillis(sp.getInt(info.nightscout.core.utils.R.string.key_protection_timeout, 0).toLong())
        // Default timeout to pass the resume check at start of an activity
        timeout = if (timeout < 1000) 1000 else timeout
        val last = lastAuthorization[protection.ordinal]
        val diff = dateUtil.now() - last
        return diff < timeout
    }

    private fun onOk(protection: ProtectionCheck.Protection) {
        lastAuthorization[protection.ordinal] = dateUtil.now()
    }

    override fun queryProtection(activity: FragmentActivity, protection: ProtectionCheck.Protection, ok: Runnable?, cancel: Runnable?, fail: Runnable?) {
        if (activeSession(protection)) {
            onOk(protection)
            ok?.run()
            return
        }

        when (ProtectionCheck.ProtectionType.values()[sp.getInt(protectionTypeResourceIDs[protection.ordinal], ProtectionCheck.ProtectionType.NONE.ordinal)]) {
            ProtectionCheck.ProtectionType.NONE            ->
                ok?.run()

            ProtectionCheck.ProtectionType.BIOMETRIC       ->
                BiometricCheck.biometricPrompt(activity, titlePassResourceIDs[protection.ordinal], { onOk(protection); ok?.run() }, cancel, fail, passwordCheck)

            ProtectionCheck.ProtectionType.MASTER_PASSWORD ->
                passwordCheck.queryPassword(activity, info.nightscout.core.ui.R.string.master_password, info.nightscout.core.utils.R.string.key_master_password, { onOk(protection); ok?.run() }, { cancel?.run() }, { fail?.run() })

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

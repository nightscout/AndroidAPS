package info.nightscout.androidaps.utils.protection

import androidx.fragment.app.FragmentActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.shared.sharedPreferences.SP
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProtectionCheck @Inject constructor(
    val sp: SP,
    val passwordCheck: PasswordCheck,
    val dateUtil: DateUtil
) {

    private var lastAuthorization = mutableListOf(0L, 0L, 0L)

    enum class Protection {
        PREFERENCES,
        APPLICATION,
        BOLUS
    }

    enum class ProtectionType {
        NONE,
        BIOMETRIC,
        MASTER_PASSWORD,
        CUSTOM_PASSWORD,
        CUSTOM_PIN
    }

    private val passwordsResourceIDs = listOf(
        R.string.key_settings_password,
        R.string.key_application_password,
        R.string.key_bolus_password)

    private val pinsResourceIDs = listOf(
        R.string.key_settings_pin,
        R.string.key_application_pin,
        R.string.key_bolus_pin)

    private val protectionTypeResourceIDs = listOf(
        R.string.key_settings_protection,
        R.string.key_application_protection,
        R.string.key_bolus_protection)

    private val titlePassResourceIDs = listOf(
        R.string.settings_password,
        R.string.application_password,
        R.string.bolus_password)

    private val titlePinResourceIDs = listOf(
        R.string.settings_pin,
        R.string.application_pin,
        R.string.bolus_pin)

    fun isLocked(protection: Protection): Boolean {
        if (activeSession(protection)) {
            return false
        }
        return when (ProtectionType.values()[sp.getInt(protectionTypeResourceIDs[protection.ordinal], ProtectionType.NONE.ordinal)]) {
            ProtectionType.NONE            -> false
            ProtectionType.BIOMETRIC       -> true
            ProtectionType.MASTER_PASSWORD -> sp.getString(R.string.key_master_password, "") != ""
            ProtectionType.CUSTOM_PASSWORD -> sp.getString(passwordsResourceIDs[protection.ordinal], "") != ""
            ProtectionType.CUSTOM_PIN -> sp.getString(pinsResourceIDs[protection.ordinal], "") != ""
        }
    }

    fun resetAuthorization() {
        lastAuthorization = mutableListOf(0L, 0L, 0L)
    }

    private fun activeSession(protection: Protection): Boolean {
        var timeout = TimeUnit.SECONDS.toMillis(sp.getInt(R.string.key_protection_timeout, 0).toLong())
        // Default timeout to pass the resume check at start of an activity
        timeout = if (timeout < 1000) 1000 else timeout
        val last = lastAuthorization[protection.ordinal]
        val diff = dateUtil.now() - last
        return diff < timeout
    }

    private fun onOk(protection: Protection) {
        lastAuthorization[protection.ordinal] = dateUtil.now()
    }

    fun queryProtection(activity: FragmentActivity, protection: Protection, ok: Runnable?, cancel: Runnable? = null, fail: Runnable? = null) {
        if (activeSession(protection)) {
            onOk(protection)
            ok?.run()
            return
        }

        when (ProtectionType.values()[sp.getInt(protectionTypeResourceIDs[protection.ordinal], ProtectionType.NONE.ordinal)]) {
            ProtectionType.NONE            ->
                ok?.run()
            ProtectionType.BIOMETRIC       ->
                BiometricCheck.biometricPrompt(activity, titlePassResourceIDs[protection.ordinal], { onOk(protection); ok?.run() }, cancel, fail, passwordCheck)
            ProtectionType.MASTER_PASSWORD ->
                passwordCheck.queryPassword(activity, R.string.master_password, R.string.key_master_password, { onOk(protection); ok?.run() }, { cancel?.run() }, { fail?.run() })
            ProtectionType.CUSTOM_PASSWORD ->
                passwordCheck.queryPassword(activity, titlePassResourceIDs[protection.ordinal], passwordsResourceIDs[protection.ordinal], { onOk(protection); ok?.run() }, { cancel?.run() }, { fail?.run() })
            ProtectionType.CUSTOM_PIN ->
                passwordCheck.queryPassword(activity, titlePinResourceIDs[protection.ordinal], pinsResourceIDs[protection.ordinal], { onOk(protection); ok?.run() }, { cancel?.run() }, { fail?.run() }, true)
        }
    }
}

package info.nightscout.androidaps.utils.protection

import androidx.fragment.app.FragmentActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProtectionCheck @Inject constructor(
    val sp: SP,
    val passwordCheck: PasswordCheck
) {

    enum class Protection {
        PREFERENCES,
        APPLICATION,
        BOLUS
    }

    enum class ProtectionType {
        NONE,
        BIOMETRIC,
        MASTER_PASSWORD,
        CUSTOM_PASSWORD
    }

    private val passwordsResourceIDs = listOf(
        R.string.key_settings_password,
        R.string.key_application_password,
        R.string.key_bolus_password)

    private val protectionTypeResourceIDs = listOf(
        R.string.key_settings_protection,
        R.string.key_application_protection,
        R.string.key_bolus_protection)

    private val titleResourceIDs = listOf(
        R.string.settings_password,
        R.string.application_password,
        R.string.bolus_password)

    fun isLocked(protection: Protection): Boolean {
        return when (ProtectionType.values()[sp.getInt(protectionTypeResourceIDs[protection.ordinal], ProtectionType.NONE.ordinal)]) {
            ProtectionType.NONE            -> false
            ProtectionType.BIOMETRIC       -> true
            ProtectionType.MASTER_PASSWORD -> sp.getString(R.string.key_master_password, "") != ""
            ProtectionType.CUSTOM_PASSWORD -> sp.getString(passwordsResourceIDs[protection.ordinal], "") != ""
        }
    }

    @JvmOverloads
    fun queryProtection(activity: FragmentActivity, protection: Protection,
                        ok: Runnable?, cancel: Runnable? = null, fail: Runnable? = null) {
        when (ProtectionType.values()[sp.getInt(protectionTypeResourceIDs[protection.ordinal], ProtectionType.NONE.ordinal)]) {
            ProtectionType.NONE            ->
                ok?.run()
            ProtectionType.BIOMETRIC       ->
                BiometricCheck.biometricPrompt(activity, titleResourceIDs[protection.ordinal], ok, cancel, fail, passwordCheck)
            ProtectionType.MASTER_PASSWORD ->
                passwordCheck.queryPassword(activity, R.string.master_password, R.string.key_master_password, { ok?.run() }, { cancel?.run() }, { fail?.run() })
            ProtectionType.CUSTOM_PASSWORD ->
                passwordCheck.queryPassword(activity, titleResourceIDs[protection.ordinal], passwordsResourceIDs[protection.ordinal], { ok?.run() }, { cancel?.run() }, { fail?.run() })
        }
    }
}
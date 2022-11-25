package info.nightscout.interfaces.protection

import androidx.fragment.app.FragmentActivity

interface ProtectionCheck {
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

    fun isLocked(protection: Protection): Boolean
    fun resetAuthorization()
    fun queryProtection(activity: FragmentActivity, protection: Protection, ok: Runnable?, cancel: Runnable? = null, fail: Runnable? = null)
}
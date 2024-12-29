package app.aaps.core.interfaces.protection

import androidx.annotation.UiThread
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
    @UiThread
    fun queryProtection(activity: FragmentActivity, protection: Protection, ok: Runnable?, cancel: Runnable? = null, fail: Runnable? = null)
}
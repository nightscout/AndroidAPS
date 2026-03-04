package app.aaps.core.interfaces.protection

import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.StateFlow

/**
 * Typealias for backward compatibility.
 * ProtectionType is now defined in core/keys module.
 */
typealias ProtectionType = app.aaps.core.keys.ProtectionType

/**
 * Result of a protection query.
 */
enum class ProtectionResult {

    GRANTED,
    DENIED,
    CANCELLED
}

/**
 * Represents a pending protection request for Compose-based UI.
 * Observed by ProtectionHost composable to show appropriate dialogs.
 */
data class ProtectionRequest(
    val id: Long,
    val protection: ProtectionCheck.Protection,
    val type: ProtectionType,
    @StringRes val titleRes: Int,
    val onResult: (ProtectionResult) -> Unit
)

interface ProtectionCheck {
    enum class Protection {
        PREFERENCES,
        APPLICATION,
        BOLUS,
        NONE
    }

    fun isLocked(protection: Protection): Boolean
    fun resetAuthorization()

    /**
     * Legacy method for Fragment-based UI. Requires activity parameter.
     */
    @UiThread
    fun queryProtection(activity: FragmentActivity, protection: Protection, ok: Runnable?, cancel: Runnable? = null, fail: Runnable? = null)

    /**
     * Compose-friendly protection query. No activity parameter needed at call site.
     * The protection dialog is shown via ProtectionHost composable observing [pendingRequest].
     */
    fun requestProtection(protection: Protection, onResult: (ProtectionResult) -> Unit)

    /**
     * Observable state for ProtectionHost composable.
     * When non-null, ProtectionHost shows the appropriate protection dialog.
     */
    val pendingRequest: StateFlow<ProtectionRequest?>

    /**
     * Called by ProtectionHost after password/PIN dialog completes.
     */
    fun completeRequest(requestId: Long, result: ProtectionResult)
}
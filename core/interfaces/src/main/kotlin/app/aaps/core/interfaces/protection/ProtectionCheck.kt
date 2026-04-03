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
 * Result of a hierarchical authorization query.
 * @param grantedLevel The highest protection level granted, or null if denied/cancelled.
 * @param outcome The result type (GRANTED, DENIED, CANCELLED).
 */
data class AuthorizationResult(
    val grantedLevel: ProtectionCheck.Protection?,
    val outcome: ProtectionResult
)

/**
 * Describes a single authentication method available at a given protection level.
 * Used by [HierarchicalProtectionRequest] to communicate available methods to the UI.
 */
data class AuthMethod(
    val level: ProtectionCheck.Protection,
    val type: ProtectionType,
    val credentialHash: String,
    val isPinInput: Boolean
)

/**
 * Represents a pending hierarchical protection request for Compose-based UI.
 * Observed by ProtectionHost composable to show the unified auth dialog.
 */
data class HierarchicalProtectionRequest(
    val id: Long,
    val minimumLevel: ProtectionCheck.Protection,
    val availableMethods: List<AuthMethod>,
    val hasBiometric: Boolean,
    val biometricGrantsLevel: ProtectionCheck.Protection?,
    val onResult: (AuthorizationResult) -> Unit
)

/**
 * Represents a pending protection request for Compose-based UI (legacy single-level).
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

    /**
     * Protection levels in hierarchical order.
     * Granting level N implicitly grants all levels with a lower [level] value.
     * [MASTER] can only be granted by entering the master password.
     */
    enum class Protection(val level: Int) {

        NONE(-1),
        APPLICATION(0),
        BOLUS(1),
        PREFERENCES(2),
        MASTER(3);

        companion object {

            fun fromLevel(level: Int): Protection? = entries.find { it.level == level }
        }
    }

    fun isLocked(protection: Protection): Boolean
    fun resetAuthorization()

    /**
     * Legacy method for Fragment-based UI. Requires activity parameter.
     */
    @UiThread
    fun queryProtection(activity: FragmentActivity, protection: Protection, ok: Runnable?, cancel: Runnable? = null, fail: Runnable? = null)

    /**
     * Compose-friendly protection query (legacy single-level).
     * The protection dialog is shown via ProtectionHost composable observing [pendingRequest].
     */
    fun requestProtection(protection: Protection, onResult: (ProtectionResult) -> Unit)

    /**
     * Hierarchical authorization. Authenticates once and returns the highest granted level
     * (>= [minimumLevel]) or null if denied/cancelled.
     * The unified auth dialog is shown via ProtectionHost observing [pendingAuthRequest].
     */
    fun requestAuthorization(minimumLevel: Protection, onResult: (AuthorizationResult) -> Unit)

    /**
     * Observable state for legacy ProtectionHost composable (single-level requests).
     * When non-null, ProtectionHost shows the appropriate protection dialog.
     */
    val pendingRequest: StateFlow<ProtectionRequest?>

    /**
     * Observable state for hierarchical auth requests.
     * When non-null, ProtectionHost shows the unified auth dialog.
     */
    val pendingAuthRequest: StateFlow<HierarchicalProtectionRequest?>

    /**
     * Called by ProtectionHost after legacy password/PIN dialog completes.
     */
    fun completeRequest(requestId: Long, result: ProtectionResult)

    /**
     * Called by ProtectionHost after unified auth dialog completes.
     */
    fun completeAuthRequest(requestId: Long, result: AuthorizationResult)
}
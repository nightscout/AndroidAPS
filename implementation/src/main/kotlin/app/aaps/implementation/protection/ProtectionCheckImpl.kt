package app.aaps.implementation.protection

import androidx.annotation.UiThread
import androidx.fragment.app.FragmentActivity
import app.aaps.core.interfaces.protection.AuthMethod
import app.aaps.core.interfaces.protection.AuthorizationResult
import app.aaps.core.interfaces.protection.HierarchicalProtectionRequest
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
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@Reusable
class ProtectionCheckImpl @Inject constructor(
    private val preferences: Preferences,
    private val passwordCheck: PasswordCheck,
    private val dateUtil: DateUtil
) : ProtectionCheck {

    // Hierarchical session: single granted level + timestamp
    @Volatile private var sessionGrantedLevel: ProtectionCheck.Protection = ProtectionCheck.Protection.NONE
    @Volatile private var sessionTimestamp: Long = 0L

    private val requestIdCounter = AtomicLong(0L)
    private val _pendingRequest = MutableStateFlow<ProtectionRequest?>(null)
    private val _pendingAuthRequest = MutableStateFlow<HierarchicalProtectionRequest?>(null)
    override val pendingRequest: StateFlow<ProtectionRequest?> = _pendingRequest.asStateFlow()
    override val pendingAuthRequest: StateFlow<HierarchicalProtectionRequest?> = _pendingAuthRequest.asStateFlow()

    // Maps from Protection level to preference keys
    private val protectionTypeKeys = mapOf(
        ProtectionCheck.Protection.PREFERENCES to IntKey.ProtectionTypeSettings,
        ProtectionCheck.Protection.APPLICATION to IntKey.ProtectionTypeApplication,
        ProtectionCheck.Protection.BOLUS to IntKey.ProtectionTypeBolus
    )

    private val passwordKeys = mapOf(
        ProtectionCheck.Protection.PREFERENCES to StringKey.ProtectionSettingsPassword,
        ProtectionCheck.Protection.APPLICATION to StringKey.ProtectionApplicationPassword,
        ProtectionCheck.Protection.BOLUS to StringKey.ProtectionBolusPassword
    )

    private val pinKeys = mapOf(
        ProtectionCheck.Protection.PREFERENCES to StringKey.ProtectionSettingsPin,
        ProtectionCheck.Protection.APPLICATION to StringKey.ProtectionApplicationPin,
        ProtectionCheck.Protection.BOLUS to StringKey.ProtectionBolusPin
    )

    private val titlePassKeys = mapOf(
        ProtectionCheck.Protection.PREFERENCES to app.aaps.core.ui.R.string.settings_password,
        ProtectionCheck.Protection.APPLICATION to app.aaps.core.ui.R.string.application_password,
        ProtectionCheck.Protection.BOLUS to app.aaps.core.ui.R.string.bolus_password
    )

    private val titlePinKeys = mapOf(
        ProtectionCheck.Protection.PREFERENCES to app.aaps.core.ui.R.string.settings_pin,
        ProtectionCheck.Protection.APPLICATION to app.aaps.core.ui.R.string.application_pin,
        ProtectionCheck.Protection.BOLUS to app.aaps.core.ui.R.string.bolus_pin
    )

    // --- Helpers ---

    /** Safe accessor for ProtectionType from stored ordinal. Returns NONE on corrupt/out-of-range values. */
    private fun protectionTypeFor(typeKey: IntKey): ProtectionType =
        ProtectionType.entries.getOrNull(preferences.get(typeKey)) ?: ProtectionType.NONE

    // --- Session management ---

    private fun activeSession(minimumLevel: ProtectionCheck.Protection): ProtectionCheck.Protection? {
        val timeout = TimeUnit.SECONDS.toMillis(preferences.get(IntKey.ProtectionTimeout).toLong())
        if (timeout <= 0) return null // 0 = always ask
        val diff = dateUtil.now() - sessionTimestamp
        if (diff >= timeout) return null
        return if (sessionGrantedLevel.level >= minimumLevel.level) sessionGrantedLevel else null
    }

    private fun onGranted(level: ProtectionCheck.Protection) {
        // Only upgrade session, never downgrade
        if (level.level > sessionGrantedLevel.level) {
            sessionGrantedLevel = level
        }
        sessionTimestamp = dateUtil.now()
    }

    override fun resetAuthorization() {
        sessionGrantedLevel = ProtectionCheck.Protection.NONE
        sessionTimestamp = 0L
    }

    // --- isLocked ---

    override fun isLocked(protection: ProtectionCheck.Protection): Boolean {
        if (protection == ProtectionCheck.Protection.NONE) return false
        if (preferences.get(StringKey.ProtectionMasterPassword).isEmpty()) return false
        if (activeSession(protection) != null) return false
        return isProtectionConfigured(protection)
    }

    private fun isProtectionConfigured(protection: ProtectionCheck.Protection): Boolean {
        if (protection == ProtectionCheck.Protection.MASTER) return true // Master is always "configured" (needs master password)
        // Hierarchy enforcement: BOLUS requires SETTINGS to be configured
        if (protection == ProtectionCheck.Protection.BOLUS && !isLevelConfigured(ProtectionCheck.Protection.PREFERENCES)) return false
        return isLevelConfigured(protection)
    }

    /** Raw check of stored preference for a specific level (no hierarchy enforcement). */
    private fun isLevelConfigured(protection: ProtectionCheck.Protection): Boolean {
        val typeKey = protectionTypeKeys[protection] ?: return false
        return when (protectionTypeFor(typeKey)) {
            ProtectionType.NONE            -> false
            ProtectionType.BIOMETRIC       -> true
            ProtectionType.MASTER_PASSWORD -> true
            ProtectionType.CUSTOM_PASSWORD -> preferences.get(passwordKeys[protection]!!).isNotEmpty()
            ProtectionType.CUSTOM_PIN      -> preferences.get(pinKeys[protection]!!).isNotEmpty()
        }
    }

    /**
     * Walk upward from [fromLevel] through APPLICATION → BOLUS → PREFERENCES,
     * returning the highest consecutive level that has no protection configured.
     */
    private fun findHighestUnprotectedLevel(fromLevel: ProtectionCheck.Protection): ProtectionCheck.Protection {
        val levelsAscending = listOf(
            ProtectionCheck.Protection.APPLICATION,
            ProtectionCheck.Protection.BOLUS,
            ProtectionCheck.Protection.PREFERENCES
        ).filter { it.level >= fromLevel.level }

        var highest = fromLevel
        for (level in levelsAscending) {
            if (!isProtectionConfigured(level)) highest = level
            else break
        }
        return highest
    }

    // --- Hierarchical authorization (new API) ---

    override fun requestAuthorization(minimumLevel: ProtectionCheck.Protection, onResult: (AuthorizationResult) -> Unit) {
        // No protection if NONE requested
        if (minimumLevel == ProtectionCheck.Protection.NONE) {
            onResult(AuthorizationResult(ProtectionCheck.Protection.NONE, ProtectionResult.GRANTED))
            return
        }

        // No master password = no protection at all
        if (preferences.get(StringKey.ProtectionMasterPassword).isEmpty()) {
            onGranted(ProtectionCheck.Protection.MASTER)
            onResult(AuthorizationResult(ProtectionCheck.Protection.MASTER, ProtectionResult.GRANTED))
            return
        }

        // Check active session
        activeSession(minimumLevel)?.let { level ->
            onGranted(level)
            onResult(AuthorizationResult(level, ProtectionResult.GRANTED))
            return
        }

        // If requested minimum level has no protection, pass immediately
        // Auto-grant the highest consecutive unprotected level
        if (!isProtectionConfigured(minimumLevel)) {
            val grantLevel = findHighestUnprotectedLevel(minimumLevel)
            onGranted(grantLevel)
            onResult(AuthorizationResult(grantLevel, ProtectionResult.GRANTED))
            return
        }

        // Cancel any pending request before emitting a new one
        _pendingAuthRequest.value?.let { pending ->
            pending.onResult(AuthorizationResult(null, ProtectionResult.CANCELLED))
        }

        // Collect auth methods at levels >= minimum
        val methods = collectAuthMethods(minimumLevel)

        // Emit request for UI
        val hasBiometric = methods.any { it.type == ProtectionType.BIOMETRIC }
        val biometricLevel = methods
            .filter { it.type == ProtectionType.BIOMETRIC }
            .maxByOrNull { it.level.level }?.level

        _pendingAuthRequest.value = HierarchicalProtectionRequest(
            id = requestIdCounter.incrementAndGet(),
            minimumLevel = minimumLevel,
            availableMethods = methods,
            hasBiometric = hasBiometric,
            biometricGrantsLevel = biometricLevel,
            onResult = { result ->
                result.grantedLevel?.let { onGranted(it) }
                _pendingAuthRequest.value = null
                onResult(result)
            }
        )
    }

    private fun collectAuthMethods(minimumLevel: ProtectionCheck.Protection): List<AuthMethod> {
        val methods = mutableListOf<AuthMethod>()

        // Check each configurable level at or above the minimum
        for ((level, typeKey) in protectionTypeKeys) {
            if (level.level < minimumLevel.level) continue
            if (!isProtectionConfigured(level)) continue // Respects hierarchy enforcement
            val type = protectionTypeFor(typeKey)

            // Skip levels configured as MASTER_PASSWORD — the always-added MASTER entry covers them
            if (type == ProtectionType.MASTER_PASSWORD) continue

            val hash = when (type) {
                ProtectionType.CUSTOM_PASSWORD -> preferences.get(passwordKeys[level]!!)
                ProtectionType.CUSTOM_PIN      -> preferences.get(pinKeys[level]!!)
                ProtectionType.BIOMETRIC       -> "" // No hash for biometric
                else                           -> "" // NONE/MASTER_PASSWORD already handled
            }
            methods.add(AuthMethod(level, type, hash, type == ProtectionType.CUSTOM_PIN))
        }

        // Master password is always available as a level 3 credential
        val masterHash = preferences.get(StringKey.ProtectionMasterPassword)
        if (masterHash.isNotEmpty()) {
            methods.add(AuthMethod(ProtectionCheck.Protection.MASTER, ProtectionType.MASTER_PASSWORD, masterHash, false))
        }

        return methods
    }

    override fun completeAuthRequest(requestId: Long, result: AuthorizationResult) {
        _pendingAuthRequest.value?.let { request ->
            if (request.id == requestId) {
                request.onResult(result)
            }
        }
    }

    // --- Legacy single-level API (checks only the requested level, not hierarchical) ---

    override fun requestProtection(protection: ProtectionCheck.Protection, onResult: (ProtectionResult) -> Unit) {
        if (protection == ProtectionCheck.Protection.NONE) {
            onResult(ProtectionResult.GRANTED)
            return
        }

        // No master password = no protection at all
        if (preferences.get(StringKey.ProtectionMasterPassword).isEmpty()) {
            onGranted(ProtectionCheck.Protection.MASTER)
            onResult(ProtectionResult.GRANTED)
            return
        }

        // Check active session
        if (activeSession(protection) != null) {
            onResult(ProtectionResult.GRANTED)
            return
        }

        // Check only the requested level's protection type
        val typeKey = protectionTypeKeys[protection]
        if (typeKey == null) {
            onResult(ProtectionResult.GRANTED)
            return
        }
        val type = protectionTypeFor(typeKey)
        if (type == ProtectionType.NONE) {
            onGranted(protection)
            onResult(ProtectionResult.GRANTED)
            return
        }

        // Cancel any pending request before emitting a new one
        _pendingRequest.value?.let { pending ->
            pending.onResult(ProtectionResult.CANCELLED)
        }

        // Emit legacy request for ProtectionHost
        val titleRes = when (type) {
            ProtectionType.CUSTOM_PIN -> titlePinKeys[protection] ?: app.aaps.core.ui.R.string.settings_pin
            else                      -> titlePassKeys[protection] ?: app.aaps.core.ui.R.string.settings_password
        }

        _pendingRequest.value = ProtectionRequest(
            id = requestIdCounter.incrementAndGet(),
            protection = protection,
            type = type,
            titleRes = titleRes,
            onResult = { result ->
                if (result == ProtectionResult.GRANTED) onGranted(protection)
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

    // --- Legacy Fragment-based API (keeps old per-type dialog behavior) ---

    @UiThread
    override fun queryProtection(
        activity: FragmentActivity,
        protection: ProtectionCheck.Protection,
        ok: Runnable?,
        cancel: Runnable?,
        fail: Runnable?
    ) {
        if (preferences.get(StringKey.ProtectionMasterPassword).isEmpty()) {
            ok?.run()
            return
        }
        if (protection == ProtectionCheck.Protection.NONE) {
            ok?.run()
            return
        }
        if (activeSession(protection) != null) {
            onGranted(protection)
            ok?.run()
            return
        }

        val typeKey = protectionTypeKeys[protection]
        if (typeKey == null) {
            ok?.run()
            return
        }

        when (protectionTypeFor(typeKey)) {
            ProtectionType.NONE            ->
                ok?.run()

            ProtectionType.BIOMETRIC       ->
                BiometricCheck.biometricPrompt(
                    activity,
                    titlePassKeys[protection] ?: app.aaps.core.ui.R.string.settings_password,
                    { onGranted(protection); ok?.run() },
                    cancel,
                    fail,
                    passwordCheck
                )

            ProtectionType.MASTER_PASSWORD ->
                passwordCheck.queryPassword(
                    activity,
                    app.aaps.core.keys.R.string.master_password,
                    StringKey.ProtectionMasterPassword,
                    { onGranted(protection); ok?.run() },
                    { cancel?.run() },
                    { fail?.run() })

            ProtectionType.CUSTOM_PASSWORD ->
                passwordCheck.queryPassword(
                    activity,
                    titlePassKeys[protection] ?: app.aaps.core.ui.R.string.settings_password,
                    passwordKeys[protection]!!,
                    { onGranted(protection); ok?.run() },
                    { cancel?.run() },
                    { fail?.run() })

            ProtectionType.CUSTOM_PIN      ->
                passwordCheck.queryPassword(
                    activity,
                    titlePinKeys[protection] ?: app.aaps.core.ui.R.string.settings_pin,
                    pinKeys[protection]!!,
                    { onGranted(protection); ok?.run() },
                    { cancel?.run() },
                    { fail?.run() },
                    true
                )
        }
    }
}

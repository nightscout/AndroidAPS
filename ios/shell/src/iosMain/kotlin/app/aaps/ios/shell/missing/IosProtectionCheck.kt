package app.aaps.ios.shell.missing

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.AuthorizationResult
import app.aaps.core.interfaces.protection.HierarchicalProtectionRequest
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionRequest
import app.aaps.core.interfaces.protection.ProtectionResult
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Placeholder, and the one in this package to be most careful about.
 *
 * Protection is what stands between someone holding the phone and the settings that decide insulin
 * delivery. There is no iOS implementation yet: no password dialog, no biometric prompt, nothing
 * that could ask.
 *
 * So this reports **nothing is locked** and grants every request, which is the wrong answer for a
 * shipped app and the right one for a first pass - the alternative is refusing everything, which
 * would leave no screen reachable and nothing to look at. It is safe only because it is safe today:
 * iOS reaches no pump and delivers no insulin. **It must not stay this way once it can.**
 *
 * Every call is logged at error level, so a run makes it obvious how often this is being leaned on.
 *
 * The real one, `ProtectionCheckImpl`, needs `PasswordCheck` and biometrics. `PasswordCheck` should
 * move to commonMain rather than be rewritten - see the request in `_docs/ios_blockers.md` - and
 * biometrics on iOS is `LAContext`, which has no counterpart here yet.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosProtectionCheck @Inject constructor(
    private val aapsLogger: AAPSLogger
) : ProtectionCheck {

    private val _pendingRequest = MutableStateFlow<ProtectionRequest?>(null)
    override val pendingRequest: StateFlow<ProtectionRequest?> = _pendingRequest.asStateFlow()

    private val _pendingAuthRequest = MutableStateFlow<HierarchicalProtectionRequest?>(null)
    override val pendingAuthRequest: StateFlow<HierarchicalProtectionRequest?> = _pendingAuthRequest.asStateFlow()

    override fun isLocked(protection: ProtectionCheck.Protection): Boolean {
        aapsLogger.notOnIosYet("ProtectionCheck.isLocked($protection) - answering NOT locked")
        return false
    }

    override fun resetAuthorization() = aapsLogger.notOnIosYet("ProtectionCheck.resetAuthorization")

    override fun requestProtection(protection: ProtectionCheck.Protection, onResult: (ProtectionResult) -> Unit) {
        aapsLogger.notOnIosYet("ProtectionCheck.requestProtection($protection) - granting without asking")
        onResult(ProtectionResult.GRANTED)
    }

    override fun requestAuthorization(minimumLevel: ProtectionCheck.Protection, onResult: (AuthorizationResult) -> Unit) {
        aapsLogger.notOnIosYet("ProtectionCheck.requestAuthorization($minimumLevel) - granting without asking")
        onResult(AuthorizationResult(grantedLevel = minimumLevel, outcome = ProtectionResult.GRANTED))
    }

    override fun completeRequest(requestId: Long, result: ProtectionResult) =
        aapsLogger.notOnIosYet("ProtectionCheck.completeRequest")

    override fun completeAuthRequest(requestId: Long, result: AuthorizationResult) =
        aapsLogger.notOnIosYet("ProtectionCheck.completeAuthRequest")
}

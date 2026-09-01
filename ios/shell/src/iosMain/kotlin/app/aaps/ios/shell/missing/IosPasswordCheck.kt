package app.aaps.ios.shell.missing

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.PasswordRequest
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Placeholder. Every prompt is refused, which is the safe direction: a caller that asked for a
 * password is told the user said no, rather than being told they said yes.
 *
 * **This one should not be written for iOS at all.** `PasswordCheckImpl` has no Android imports -
 * it is 162 lines over `Preferences`, `CryptoUtil`, `RxBus` and `TextResolver`, and it is in
 * androidMain only because `CryptoUtil` is. It uses exactly two things from it, `hashPassword` and
 * `checkPassword`, which are now `PasswordHasher` on both platforms. Swapping that one constructor
 * parameter should let the class move to commonMain and iOS would get it for free. That request is
 * in `_docs/ios_blockers.md`; this class is here only so the graph builds until then.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosPasswordCheck @Inject constructor(
    private val aapsLogger: AAPSLogger
) : PasswordCheck {

    private val _request = MutableStateFlow<PasswordRequest?>(null)
    override val request: StateFlow<PasswordRequest?> = _request.asStateFlow()

    override fun queryPassword(
        label: TextRef,
        preference: StringPreferenceKey,
        ok: ((String) -> Unit)?,
        cancel: (() -> Unit)?,
        fail: (() -> Unit)?,
        pinInput: Boolean
    ) {
        aapsLogger.notOnIosYet("PasswordCheck.queryPassword")
        cancel?.invoke()
    }

    override fun setPassword(
        label: TextRef,
        preference: StringPreferenceKey,
        ok: ((String) -> Unit)?,
        cancel: (() -> Unit)?,
        clear: (() -> Unit)?,
        pinInput: Boolean
    ) {
        aapsLogger.notOnIosYet("PasswordCheck.setPassword")
        cancel?.invoke()
    }

    override fun queryAnyPassword(
        label: TextRef,
        preference: StringPreferenceKey,
        passwordExplanation: TextRef?,
        passwordWarning: TextRef?,
        ok: ((String) -> Unit)?,
        cancel: (() -> Unit)?
    ) {
        aapsLogger.notOnIosYet("PasswordCheck.queryAnyPassword")
        cancel?.invoke()
    }
}

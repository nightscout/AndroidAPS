package app.aaps.implementation.protection

import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.PasswordRequest
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.UiStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

/**
 * Publishes the prompt; `PasswordCheckHost` draws it.
 *
 * This used to build an `android.app.Dialog` around a `ComposeView`, with a hand written
 * `LifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner` to satisfy the view tree, and a
 * 100 ms delay after every dismissal so the caller's callback ran once the window was gone. All of
 * that was the cost of starting Compose from outside a composition. Publishing a request instead
 * removes the dialog, the owner, the delay and the `Context` - the decisions about what a password
 * MEANS all stay here.
 */
// Must be a singleton, not @Reusable: the caller that asks for a password and the host that draws it
// have to see the same [request] flow.
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class PasswordCheckImpl @Inject constructor(
    private val preferences: Preferences,
    private val cryptoUtil: CryptoUtil,
    private val rxBus: RxBus,
    private val rh: ResourceHelper
) : PasswordCheck {

    @Inject lateinit var exportPasswordDataStore: ExportPasswordDataStore

    private val _request = MutableStateFlow<PasswordRequest?>(null)
    override val request: StateFlow<PasswordRequest?> = _request.asStateFlow()

    private fun dismiss() {
        _request.value = null
    }

    private fun snack(message: TextRef, type: EventShowSnackbar.Type) {
        rxBus.send(EventShowSnackbar(rh.gs(message), type))
    }

    override fun queryPassword(
        label: TextRef,
        preference: StringPreferenceKey,
        ok: ((String) -> Unit)?,
        cancel: (() -> Unit)?,
        fail: (() -> Unit)?,
        pinInput: Boolean
    ) {
        val password = preferences.get(preference)
        if (password == "") {
            ok?.invoke("")
            return
        }

        _request.value = PasswordRequest.Query(
            label = label,
            pinInput = pinInput,
            onConfirm = { enteredPassword ->
                if (cryptoUtil.checkPassword(enteredPassword, password)) {
                    dismiss()
                    ok?.invoke(enteredPassword)
                } else {
                    // Deliberately does NOT dismiss: a wrong password leaves the prompt up so the
                    // user can try again, exactly as before.
                    snack(if (pinInput) UiStrings.wrongpin else UiStrings.wrongpassword, EventShowSnackbar.Type.Error)
                    fail?.invoke()
                }
            },
            onCancel = {
                dismiss()
                cancel?.invoke()
            }
        )
    }

    override fun setPassword(
        label: TextRef,
        preference: StringPreferenceKey,
        ok: ((String) -> Unit)?,
        cancel: (() -> Unit)?,
        clear: (() -> Unit)?,
        pinInput: Boolean
    ) {
        _request.value = PasswordRequest.Set(
            label = label,
            pinInput = pinInput,
            onConfirm = { enteredPassword, enteredPassword2 ->
                when {
                    enteredPassword != enteredPassword2 -> {
                        // Mismatch keeps the prompt open so the entries can be corrected.
                        snack(if (pinInput) UiStrings.pin_dont_match else UiStrings.passwords_dont_match, EventShowSnackbar.Type.Error)
                    }

                    enteredPassword.isNotEmpty()        -> {
                        preferences.put(preference, cryptoUtil.hashPassword(enteredPassword))
                        exportPasswordDataStore.clearPasswordDataStore()
                        snack(if (pinInput) UiStrings.pin_set else UiStrings.password_set, EventShowSnackbar.Type.Success)
                        dismiss()
                        ok?.invoke(enteredPassword)
                    }

                    // Empty entry means "clear it", but only if there was one to clear.
                    preferences.getIfExists(preference) != null -> {
                        preferences.remove(preference)
                        snack(if (pinInput) UiStrings.pin_cleared else UiStrings.password_cleared, EventShowSnackbar.Type.Success)
                        dismiss()
                        clear?.invoke()
                    }

                    else                                -> {
                        snack(if (pinInput) UiStrings.pin_not_changed else UiStrings.password_not_changed, EventShowSnackbar.Type.Warning)
                        dismiss()
                        cancel?.invoke()
                    }
                }
            },
            onCancel = {
                snack(if (pinInput) UiStrings.pin_not_changed else UiStrings.password_not_changed, EventShowSnackbar.Type.Info)
                dismiss()
                cancel?.invoke()
            }
        )
    }

    override fun queryAnyPassword(
        label: TextRef,
        preference: StringPreferenceKey,
        passwordExplanation: TextRef?,
        passwordWarning: TextRef?,
        ok: ((String) -> Unit)?,
        cancel: (() -> Unit)?
    ) {
        _request.value = PasswordRequest.QueryAny(
            label = label,
            explanation = passwordExplanation,
            warning = passwordWarning,
            onConfirm = { enteredPassword ->
                dismiss()
                ok?.invoke(enteredPassword)
            },
            onCancel = {
                dismiss()
                cancel?.invoke()
            }
        )
    }
}

package app.aaps.implementation.protection

import android.app.Dialog
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.dialogs.QueryAnyPasswordDialog
import app.aaps.core.ui.compose.dialogs.QueryPasswordDialog
import app.aaps.core.ui.compose.dialogs.SetPasswordDialog
import app.aaps.core.ui.toast.ToastUtils
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@Reusable
class PasswordCheckImpl @Inject constructor(
    private val preferences: Preferences,
    private val cryptoUtil: CryptoUtil,
    private val rxBus: RxBus
) : PasswordCheck {

    @Inject lateinit var exportPasswordDataStore: ExportPasswordDataStore

    /**
     * A custom owner class that provides the necessary platform owners for a ComposeView
     * hosted in a custom Dialog.
     */
    private class ComposeDialogOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val _viewModelStore = ViewModelStore()
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        init {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val viewModelStore: ViewModelStore
            get() = _viewModelStore

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            _viewModelStore.clear()
        }
    }

    /**
    Asks for "managed" kind of password, checking if it is valid.
     */
    override fun queryPassword(
        context: Context,
        @StringRes labelId: Int,
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

        val dialog = Dialog(context)
        val owner = ComposeDialogOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner))
            setContent {
                CompositionLocalProvider(
                    LocalPreferences provides preferences
                ) {
                    AapsTheme {
                        QueryPasswordDialog(
                            title = context.getString(labelId),
                            pinInput = pinInput,
                            onConfirm = { enteredPassword ->
                                if (cryptoUtil.checkPassword(enteredPassword, password)) {
                                    dialog.dismiss()
                                    CoroutineScope(Dispatchers.Main).launch {
                                        delay(100)
                                        ok?.invoke(enteredPassword)
                                    }
                                } else {
                                    val msg = if (pinInput) app.aaps.core.ui.R.string.wrongpin else app.aaps.core.ui.R.string.wrongpassword
                                    ToastUtils.errorToast(context, context.getString(msg))
                                    fail?.invoke()
                                }
                            },
                            onCancel = {
                                dialog.dismiss()
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(100)
                                    cancel?.invoke()
                                }
                            }
                        )
                    }
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { owner.destroy() }
        dialog.show()
    }

    override fun setPassword(
        context: Context,
        @StringRes labelId: Int,
        preference: StringPreferenceKey,
        ok: ((String) -> Unit)?,
        cancel: (() -> Unit)?,
        clear: (() -> Unit)?,
        pinInput: Boolean
    ) {
        val dialog = Dialog(context)
        val owner = ComposeDialogOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner))
            setContent {
                CompositionLocalProvider(
                    LocalPreferences provides preferences
                ) {
                    AapsTheme {
                        SetPasswordDialog(
                            title = context.getString(labelId),
                            pinInput = pinInput,
                            onConfirm = { enteredPassword, enteredPassword2 ->
                                if (enteredPassword != enteredPassword2) {
                                    val msg = if (pinInput) app.aaps.core.ui.R.string.pin_dont_match else app.aaps.core.ui.R.string.passwords_dont_match
                                    ToastUtils.errorToast(context, context.getString(msg))
                                } else if (enteredPassword.isNotEmpty()) {
                                    preferences.put(preference, cryptoUtil.hashPassword(enteredPassword))
                                    exportPasswordDataStore.clearPasswordDataStore(context)
                                    val msg = if (pinInput) app.aaps.core.ui.R.string.pin_set else app.aaps.core.ui.R.string.password_set
                                    ToastUtils.okToast(context, context.getString(msg))
                                    dialog.dismiss()
                                    CoroutineScope(Dispatchers.Main).launch {
                                        delay(100)
                                        ok?.invoke(enteredPassword)
                                    }
                                } else {
                                    if (preferences.getIfExists(preference) != null) {
                                        preferences.remove(preference)
                                        val msg = if (pinInput) app.aaps.core.ui.R.string.pin_cleared else app.aaps.core.ui.R.string.password_cleared
                                        ToastUtils.graphicalToast(context, context.getString(msg), app.aaps.core.ui.R.drawable.ic_toast_delete_confirm)
                                        dialog.dismiss()
                                        CoroutineScope(Dispatchers.Main).launch {
                                            delay(100)
                                            clear?.invoke()
                                        }
                                    } else {
                                        val msg = if (pinInput) app.aaps.core.ui.R.string.pin_not_changed else app.aaps.core.ui.R.string.password_not_changed
                                        ToastUtils.warnToast(context, context.getString(msg))
                                        dialog.dismiss()
                                        CoroutineScope(Dispatchers.Main).launch {
                                            delay(100)
                                            cancel?.invoke()
                                        }
                                    }
                                }
                            },
                            onCancel = {
                                val msg = if (pinInput) app.aaps.core.ui.R.string.pin_not_changed else app.aaps.core.ui.R.string.password_not_changed
                                ToastUtils.infoToast(context, msg)
                                dialog.dismiss()
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(100)
                                    cancel?.invoke()
                                }
                            }
                        )
                    }
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { owner.destroy() }
        dialog.show()
    }

    /**
    Prompt free-form password, with additional help and warning messages.
    Preference ID (preference) is used only to generate ID for password managers,
    since this query does NOT check validity of password.
     */
    override fun queryAnyPassword(
        context: Context,
        @StringRes labelId: Int,
        preference: StringPreferenceKey,
        @StringRes passwordExplanation: Int?,
        @StringRes passwordWarning: Int?,
        ok: ((String) -> Unit)?,
        cancel: (() -> Unit)?
    ) {
        val dialog = Dialog(context)
        val owner = ComposeDialogOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner))
            setContent {
                CompositionLocalProvider(
                    LocalPreferences provides preferences
                ) {
                    AapsTheme {
                        QueryAnyPasswordDialog(
                            title = context.getString(labelId),
                            passwordExplanation = passwordExplanation?.let { context.getString(it) },
                            passwordWarning = passwordWarning?.let { context.getString(it) },
                            onConfirm = { enteredPassword ->
                                dialog.dismiss()
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(100)
                                    ok?.invoke(enteredPassword)
                                }
                            },
                            onCancel = {
                                dialog.dismiss()
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(100)
                                    cancel?.invoke()
                                }
                            }
                        )
                    }
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { owner.destroy() }
        dialog.show()
    }
}

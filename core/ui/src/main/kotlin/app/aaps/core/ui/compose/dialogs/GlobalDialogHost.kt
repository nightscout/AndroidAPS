package app.aaps.core.ui.compose.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import kotlinx.coroutines.CompletableDeferred

/**
 * Root-level dialog host that subscribes to [EventShowDialog] on [rxBus] and
 * renders the corresponding Compose dialog.
 *
 * Place once at the root of `Activity.setContent { }` (alongside
 * [GlobalSnackbarHost]). Any code with [RxBus] injected can call
 * `rxBus.send(EventShowDialog.Xxx(...))` from any thread.
 *
 * Only one dialog renders at a time: the collector uses a
 * [CompletableDeferred] per event and `await()`s the dismiss, so rapid sends
 * queue naturally through `Flow.collect`'s sequential semantics instead of
 * clobbering an on-screen dialog.
 *
 * Scoped to `Lifecycle.State.STARTED`: the collector is cancelled when the
 * activity goes to the background, so an in-flight event arrives at the next
 * foreground activity rather than being shown on a stopped one.
 *
 * [CompletableDeferred] uses [Boolean?]:
 *  - `true`  = positive action (OK / Yes)
 *  - `false` = negative action (Cancel in OkCancel / No in YesNoCancel)
 *  - `null`  = neutral dismiss (Cancel button in YesNoCancel — distinct from No)
 */
@Composable
fun GlobalDialogHost(rxBus: RxBus) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var current by remember { mutableStateOf<Pair<EventShowDialog, CompletableDeferred<Boolean?>>?>(null) }

    LaunchedEffect(rxBus, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            rxBus.toFlow(EventShowDialog::class.java).collect { event ->
                val choice = CompletableDeferred<Boolean?>()
                try {
                    current = event to choice
                    val confirmed = choice.await()
                    current = null
                    when (event) {
                        is EventShowDialog.Ok          -> if (confirmed != false) event.onOk?.invoke()
                        is EventShowDialog.OkCancel    -> if (confirmed != false) event.onOk() else event.onCancel?.invoke()
                        is EventShowDialog.YesNoCancel -> when (confirmed) {
                            true  -> event.onYes()
                            false -> event.onNo?.invoke()
                            null  -> Unit
                        }

                        is EventShowDialog.Error       -> if (confirmed != false) event.onPositive?.invoke() else event.onDismiss?.invoke()
                    }
                } finally {
                    current = null
                }
            }
        }
    }

    val active = current ?: return
    val (event, choice) = active

    when (event) {
        is EventShowDialog.Ok          ->
            OkDialog(
                title = event.title,
                message = event.message,
                onDismiss = { choice.complete(true) }
            )

        is EventShowDialog.OkCancel    -> {
            // Structured confirmation lines win and are themed here (render time); otherwise fall back to
            // a pre-built AnnotatedString, else a plain/HTML String. Local vals so the cross-module
            // properties can smart-cast.
            val lines = event.confirmationLines
            val msg = event.message
            // No element on a bare EventShowDialog — wizard lines don't use PRIMARY, so the default is unused.
            val themed = if (lines != null) lines.toAnnotatedString(primaryColor = MaterialTheme.colorScheme.primary) else null
            when {
                themed != null         ->
                    OkCancelDialog(
                        title = event.title,
                        message = themed,
                        secondMessage = event.secondMessage,
                        icon = event.icon,
                        onConfirm = { choice.complete(true) },
                        onDismiss = { choice.complete(false) }
                    )

                msg is AnnotatedString ->
                    OkCancelDialog(
                        title = event.title,
                        message = msg,
                        secondMessage = event.secondMessage,
                        icon = event.icon,
                        onConfirm = { choice.complete(true) },
                        onDismiss = { choice.complete(false) }
                    )

                else                   ->
                    OkCancelDialog(
                        title = event.title,
                        message = msg.toString(),
                        secondMessage = event.secondMessage,
                        icon = event.icon,
                        onConfirm = { choice.complete(true) },
                        onDismiss = { choice.complete(false) }
                    )
            }
        }

        is EventShowDialog.YesNoCancel ->
            YesNoCancelDialog(
                title = event.title,
                message = event.message,
                onYes = { choice.complete(true) },
                onNo = { choice.complete(false) },
                onCancel = { choice.complete(null) }
            )

        is EventShowDialog.Error       ->
            ErrorDialog(
                title = event.title,
                message = event.message,
                positiveButton = event.positiveButton,
                onPositive = { choice.complete(true) },
                onDismiss = { choice.complete(false) }
            )
    }
}

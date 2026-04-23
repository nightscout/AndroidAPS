package app.aaps.core.ui.compose.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 */
@Composable
fun GlobalDialogHost(rxBus: RxBus) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var current by remember { mutableStateOf<Pair<EventShowDialog, CompletableDeferred<Unit>>?>(null) }

    LaunchedEffect(rxBus, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            rxBus.toFlow(EventShowDialog::class.java).collect { event ->
                val completion = CompletableDeferred<Unit>()
                current = event to completion
                completion.await()
                current = null
            }
        }
    }

    val active = current ?: return
    val (event, done) = active

    when (event) {
        is EventShowDialog.Ok          ->
            OkDialog(
                title = event.title,
                message = event.message,
                onDismiss = {
                    event.onOk?.invoke()
                    done.complete(Unit)
                }
            )

        is EventShowDialog.OkCancel    -> {
            val onConfirm: () -> Unit = {
                event.onOk.invoke()
                done.complete(Unit)
            }
            val onDismiss: () -> Unit = {
                event.onCancel?.invoke()
                done.complete(Unit)
            }
            // Branch on message type: AnnotatedString skips HTML parsing entirely,
            // String goes through the legacy HTML path for resources still shipping
            // <b>/<br>/<font> markup.
            when (val msg = event.message) {
                is androidx.compose.ui.text.AnnotatedString ->
                    OkCancelDialog(
                        title = event.title,
                        message = msg,
                        secondMessage = event.secondMessage,
                        icon = event.icon,
                        onConfirm = onConfirm,
                        onDismiss = onDismiss
                    )

                else                                        ->
                    OkCancelDialog(
                        title = event.title,
                        message = msg.toString(),
                        secondMessage = event.secondMessage,
                        icon = event.icon,
                        onConfirm = onConfirm,
                        onDismiss = onDismiss
                    )
            }
        }

        is EventShowDialog.YesNoCancel ->
            YesNoCancelDialog(
                title = event.title,
                message = event.message,
                onYes = {
                    event.onYes.invoke()
                    done.complete(Unit)
                },
                onNo = {
                    event.onNo?.invoke()
                    done.complete(Unit)
                },
                onCancel = {
                    done.complete(Unit)
                }
            )

        is EventShowDialog.Error       ->
            ErrorDialog(
                title = event.title,
                message = event.message,
                positiveButton = event.positiveButton,
                onPositive = {
                    event.onPositive?.invoke()
                    done.complete(Unit)
                },
                onDismiss = {
                    event.onDismiss?.invoke()
                    done.complete(Unit)
                }
            )
    }
}

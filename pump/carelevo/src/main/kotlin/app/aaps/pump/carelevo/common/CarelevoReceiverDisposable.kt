package app.aaps.pump.carelevo.common

import android.content.BroadcastReceiver
import android.content.Context
import io.reactivex.rxjava3.disposables.Disposable

/**
 * Rx [Disposable] that unregisters [receiver] on dispose — the teardown half of
 * [CarelevoObserveReceiver]'s register-on-subscribe, preventing the classic leaked-receiver bug
 * when the plugin's subscription is cleared in `onStop()`.
 */
class CarelevoReceiverDisposable(
    private val context: Context,
    private val receiver: BroadcastReceiver
) : Disposable {

    private var isDisposed = false

    @Synchronized
    override fun dispose() {
        context.unregisterReceiver(receiver)
        isDisposed = true
    }

    @Synchronized
    override fun isDisposed(): Boolean {
        return isDisposed
    }
}
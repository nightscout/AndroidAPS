package info.nightscout.androidaps.plugins.pump.carelevo.common

import android.content.BroadcastReceiver
import android.content.Context
import io.reactivex.rxjava3.disposables.Disposable

class CarelevoReceiverDisposable(
    private val context : Context,
    private val receiver : BroadcastReceiver
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
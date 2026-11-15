package app.aaps.pump.carelevo.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer

/**
 * Rx wrapper around a [BroadcastReceiver]: each subscription registers a receiver for [filter] and
 * emits every matching [Intent]; disposing the subscription unregisters it (via
 * [CarelevoReceiverDisposable]). Used by the plugin to observe Bluetooth adapter state changes.
 *
 * Registered NOT_EXPORTED — the only consumers listen to protected system broadcasts
 * (e.g. `BluetoothAdapter.ACTION_STATE_CHANGED`), which the system delivers regardless; nothing
 * here should ever be targetable by other apps.
 */
class CarelevoObserveReceiver(
    private val context: Context,
    private val filter: IntentFilter
) : Observable<Intent>() {

    override fun subscribeActual(observer: Observer<in Intent>) {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    if (filter.matchAction(it.action)) {
                        observer.onNext(it)
                    }
                }
            }
        }.apply {
            observer.onSubscribe(CarelevoReceiverDisposable(context, this))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                context.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED)
            else
                context.registerReceiver(this, filter)
        }
    }
}

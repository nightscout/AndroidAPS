package app.aaps.pump.eopatch.ui.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.CheckResult
import io.reactivex.rxjava3.android.MainThreadDisposable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer

class RxBroadcastReceiver private constructor() {
    internal class BroadcastReceiverObservable : Observable<Intent> {

        private val context: Context
        private val intentFilter: IntentFilter
        private val abortBroadcast: Boolean

        constructor(context: Context, intentFilter: IntentFilter) {
            this.context = context
            this.intentFilter = intentFilter
            abortBroadcast = false
        }

        constructor(context: Context, intentFilter: IntentFilter, abortBroadcast: Boolean) {
            this.context = context
            this.intentFilter = intentFilter
            this.abortBroadcast = abortBroadcast
        }

        override fun subscribeActual(observer: Observer<in Intent>) {
            val listener = Listener(context, observer)
            observer.onSubscribe(listener)
            context.registerReceiver(listener.receiver, intentFilter)
        }

        internal inner class Listener(private val context: Context, private val observer: Observer<in Intent>) : MainThreadDisposable() {

            val receiver: BroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (!isDisposed) {
                        observer.onNext(intent)
                        if (abortBroadcast) abortBroadcast()
                    }
                }
            }

            override fun onDispose() {
                context.unregisterReceiver(receiver)
            }
        }
    }

    companion object {

        @CheckResult
        fun create(context: Context, intentFilter: IntentFilter): Observable<Intent> = BroadcastReceiverObservable(context, intentFilter)

        @CheckResult
        fun create(context: Context, intentFilter: IntentFilter, abortBroadcast: Boolean): Observable<Intent> = BroadcastReceiverObservable(context, intentFilter, abortBroadcast)
    }
}
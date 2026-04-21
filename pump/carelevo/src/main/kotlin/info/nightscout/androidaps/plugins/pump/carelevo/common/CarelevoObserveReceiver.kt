package info.nightscout.androidaps.plugins.pump.carelevo.common

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer

class CarelevoObserveReceiver(
    private val context : Context,
    private val filter : IntentFilter
) : Observable<Intent>() {

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun subscribeActual(observer: Observer<in Intent>) {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    if(filter.matchAction(it.action)) {
                        observer.onNext(it)
                    }
                }
            }
        }.apply {
            observer.onSubscribe(CarelevoReceiverDisposable(context, this))
            context.registerReceiver(this, filter)
        }
    }

}
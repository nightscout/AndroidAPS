package app.aaps.pump.carelevo.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Registers a [BroadcastReceiver] for [filter] that gives every matching [Intent] to [onIntent], on the main thread.
 * Used by the plugin to follow the Bluetooth adapter state. The caller unregisters the returned receiver with
 * [Context.unregisterReceiver] when it stops, or the receiver leaks.
 *
 * Registered NOT_EXPORTED — the only consumers listen to protected system broadcasts
 * (e.g. `BluetoothAdapter.ACTION_STATE_CHANGED`), which the system delivers regardless; nothing
 * here should ever be targetable by other apps.
 */
fun registerCarelevoReceiver(context: Context, filter: IntentFilter, onIntent: (Intent) -> Unit): BroadcastReceiver {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                if (filter.matchAction(it.action)) onIntent(it)
            }
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    else
        context.registerReceiver(receiver, filter)
    return receiver
}

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
 * Registered EXPORTED, so give it only protected broadcasts of the Bluetooth stack, as
 * `BluetoothAdapter.ACTION_STATE_CHANGED`. No other app can send those. The Bluetooth process sends them with its own
 * uid (1002), not the system uid, and Android gives a not-exported receiver only the broadcasts of the system and of
 * this app: registered not-exported, the plugin never saw Bluetooth turned off or on.
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
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    else
        context.registerReceiver(receiver, filter)
    return receiver
}

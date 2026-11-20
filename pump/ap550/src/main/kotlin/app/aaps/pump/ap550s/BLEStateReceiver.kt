package app.aaps.pump.ap550s

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class BLEStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val BLEState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)
        when (BLEState) {
            BluetoothAdapter.STATE_ON          ->                 // Bluetooth is on
                Log.d("BLEStateReceiver", "Bluetooth is on")

            BluetoothAdapter.STATE_TURNING_OFF -> {
                // Bluetooth is turning off
                Log.d("BLEStateReceiver", "Bluetooth is off")
                val localIntent = Intent()
                localIntent.setAction("com.localbroadcast.BLE_SWITCH")
                localIntent.putExtra("reason", "bluetooth is off")
                LocalBroadcastManager.getInstance(context).sendBroadcast(localIntent)
            }
        }
    }

    companion object {

        private val receiver = BLEStateReceiver()

        fun register(context: Context) {
            val filter = IntentFilter()
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }

        fun unregister(context: Context) {
            context.unregisterReceiver(receiver)
        }
    }
}

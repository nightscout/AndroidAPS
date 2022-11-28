package info.nightscout.androidaps.receivers

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.core.utils.extensions.safeGetParcelableExtra
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventBTChange
import javax.inject.Inject

class BTReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var rxBus: RxBus

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val device = intent.safeGetParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED    ->
                rxBus.send(EventBTChange(EventBTChange.Change.CONNECT, deviceName = device.name, deviceAddress = device.address))
            BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                rxBus.send(EventBTChange(EventBTChange.Change.DISCONNECT, deviceName = device.name, deviceAddress = device.address))
        }
    }
}
package app.aaps.receivers

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import app.aaps.core.interfaces.androidPermissions.AndroidPermission
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventBTChange
import app.aaps.core.utils.extensions.safeGetParcelableExtra
import dagger.android.DaggerBroadcastReceiver
import javax.inject.Inject

class BTReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var androidPermission: AndroidPermission

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        processIntent(context, intent)
    }

    @SuppressLint("MissingPermission")
    @VisibleForTesting
    fun processIntent(context: Context, intent: Intent) {
        val device = intent.safeGetParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return

        if (!androidPermission.permissionNotGranted(context, Manifest.permission.BLUETOOTH_CONNECT)) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED    ->
                    rxBus.send(EventBTChange(EventBTChange.Change.CONNECT, deviceName = device.name, deviceAddress = device.address))

                BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                    rxBus.send(EventBTChange(EventBTChange.Change.DISCONNECT, deviceName = device.name, deviceAddress = device.address))
            }
        }
    }
}
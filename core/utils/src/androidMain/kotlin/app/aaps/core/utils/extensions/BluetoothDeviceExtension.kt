package app.aaps.core.utils.extensions

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context

/**
 * Open a GATT connection to this device.
 *
 * API 37 deprecated every `connectGatt` overload that takes a [Context] in favour of
 * `connectGatt(BluetoothGattConnectionSettings, Executor, BluetoothGattCallback)`. That replacement
 * class does not exist below API 37, and our minSdk is 31, so the old call is still the only one we
 * can use. Keeping it in one place means there is a single suppression and a single spot to migrate
 * once minSdk reaches 37.
 *
 * Note for that future migration: the new overload delivers callbacks on the executor that is passed
 * in, while the old one uses a platform thread. Pump drivers rely on the current threading, so the
 * switch is not a drop-in replacement.
 *
 * @param transport one of `BluetoothDevice.TRANSPORT_*`. The default matches the three argument
 *   overload, which uses `TRANSPORT_AUTO`.
 */
@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
fun BluetoothDevice.connectGattCompat(
    context: Context,
    autoConnect: Boolean,
    callback: BluetoothGattCallback,
    transport: Int = BluetoothDevice.TRANSPORT_AUTO
): BluetoothGatt? = connectGatt(context, autoConnect, callback, transport)

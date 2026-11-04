package no.nordicsemi.android.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * AAPS change to the Nordic library: what {@link BleManagerHandler} needs from a GATT connection.
 * The methods have the same meaning as in {@link BluetoothGatt}. {@link RealGattLink} is the real one.
 */
interface GattLink {

	@NonNull
	BluetoothDevice getDevice();

	/** The Android object for the library hooks that take one. */
	BluetoothGatt realGatt();

	boolean connect();

	void disconnect();

	void close();

	boolean discoverServices();

	@Nullable
	BluetoothGattService getService(@NonNull UUID uuid);

	boolean setCharacteristicNotification(@NonNull BluetoothGattCharacteristic characteristic, boolean enable);

	boolean readCharacteristic(@NonNull BluetoothGattCharacteristic characteristic);

	boolean writeCharacteristic(@NonNull BluetoothGattCharacteristic characteristic);

	int writeCharacteristic(@NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int writeType);

	boolean readDescriptor(@NonNull BluetoothGattDescriptor descriptor);

	boolean writeDescriptor(@NonNull BluetoothGattDescriptor descriptor);

	int writeDescriptor(@NonNull BluetoothGattDescriptor descriptor, @NonNull byte[] value);

	boolean beginReliableWrite();

	boolean executeReliableWrite();

	void abortReliableWrite();

	boolean requestMtu(int mtu);

	boolean requestConnectionPriority(int priority);

	void setPreferredPhy(int txPhy, int rxPhy, int phyOptions);

	void readPhy();

	boolean readRemoteRssi();
}

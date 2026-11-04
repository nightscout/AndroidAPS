package no.nordicsemi.android.ble;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.NonNull;

/**
 * AAPS change to the Nordic library: the events of {@code BluetoothGattCallback}, with a {@link GattLink}
 * in place of the {@code BluetoothGatt} parameter. {@link BleManagerHandler} implements this.
 */
abstract class GattLinkCallback {

	public void onConnectionStateChange(@NonNull GattLink gatt, int status, int newState) {
	}

	public void onServicesDiscovered(@NonNull GattLink gatt, int status) {
	}

	public void onServiceChanged(@NonNull GattLink gatt) {
	}

	public void onCharacteristicRead(GattLink gatt, BluetoothGattCharacteristic characteristic, int status) {
	}

	public void onCharacteristicRead(@NonNull GattLink gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] data, int status) {
	}

	public void onCharacteristicWrite(GattLink gatt, BluetoothGattCharacteristic characteristic, int status) {
	}

	public void onReliableWriteCompleted(@NonNull GattLink gatt, int status) {
	}

	public void onDescriptorRead(GattLink gatt, BluetoothGattDescriptor descriptor, int status) {
	}

	public void onDescriptorWrite(GattLink gatt, BluetoothGattDescriptor descriptor, int status) {
	}

	public void onCharacteristicChanged(GattLink gatt, BluetoothGattCharacteristic characteristic) {
	}

	public void onCharacteristicChanged(@NonNull GattLink gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] data) {
	}

	public void onMtuChanged(@NonNull GattLink gatt, int mtu, int status) {
	}

	public void onConnectionUpdated(@NonNull GattLink gatt, int interval, int latency, int timeout, int status) {
	}

	public void onPhyUpdate(@NonNull GattLink gatt, int txPhy, int rxPhy, int status) {
	}

	public void onPhyRead(@NonNull GattLink gatt, int txPhy, int rxPhy, int status) {
	}

	public void onReadRemoteRssi(@NonNull GattLink gatt, int rssi, int status) {
	}
}

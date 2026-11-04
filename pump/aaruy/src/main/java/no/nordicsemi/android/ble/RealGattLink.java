package no.nordicsemi.android.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Build;

import java.util.UUID;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * AAPS change to the Nordic library: the real radio. Every call goes to Android's {@link BluetoothGatt}
 * and every event comes back from {@link #androidCallback}, so the library behaves as in the original.
 */
@SuppressLint("MissingPermission")
final class RealGattLink implements GattLink {

	private final GattLinkCallback callback;
	private BluetoothGatt gatt;

	RealGattLink(@NonNull GattLinkCallback callback) {
		this.callback = callback;
	}

	/** @return this link, or null if Android did not give a connection. */
	@Nullable
	RealGattLink wrap(@Nullable BluetoothGatt gatt) {
		if (gatt == null) return null;
		this.gatt = gatt;
		return this;
	}

	/** An event can come before {@code connectGatt} has returned. */
	private GattLink link(BluetoothGatt gatt) {
		if (this.gatt == null) this.gatt = gatt;
		return this;
	}

	final BluetoothGattCallback androidCallback = new BluetoothGattCallback() {

		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			callback.onConnectionStateChange(link(gatt), status, newState);
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			callback.onServicesDiscovered(link(gatt), status);
		}

		/** Requires API 31+. Android calls this by name on older versions that have it hidden. */
		@Keep
		public void onServiceChanged(@NonNull BluetoothGatt gatt) {
			callback.onServiceChanged(link(gatt));
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			callback.onCharacteristicRead(link(gatt), characteristic, status);
		}

		@Override
		public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] data, int status) {
			callback.onCharacteristicRead(link(gatt), characteristic, data, status);
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			callback.onCharacteristicWrite(link(gatt), characteristic, status);
		}

		@Override
		public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
			callback.onReliableWriteCompleted(link(gatt), status);
		}

		@Override
		public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			callback.onDescriptorRead(link(gatt), descriptor, status);
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			callback.onDescriptorWrite(link(gatt), descriptor, status);
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			callback.onCharacteristicChanged(link(gatt), characteristic);
		}

		@Override
		public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] data) {
			callback.onCharacteristicChanged(link(gatt), characteristic, data);
		}

		@Override
		public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
			callback.onMtuChanged(link(gatt), mtu, status);
		}

		/** A hidden Android method: no override, and the name and parameters must not change. */
		@RequiresApi(api = Build.VERSION_CODES.O)
		@Keep
		public void onConnectionUpdated(@NonNull BluetoothGatt gatt, int interval, int latency, int timeout, int status) {
			callback.onConnectionUpdated(link(gatt), interval, latency, timeout, status);
		}

		@Override
		public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
			callback.onPhyUpdate(link(gatt), txPhy, rxPhy, status);
		}

		@Override
		public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
			callback.onPhyRead(link(gatt), txPhy, rxPhy, status);
		}

		@Override
		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			callback.onReadRemoteRssi(link(gatt), rssi, status);
		}
	};

	@NonNull
	@Override
	public BluetoothDevice getDevice() {
		return gatt.getDevice();
	}

	@Override
	public BluetoothGatt realGatt() {
		return gatt;
	}

	@Override
	public boolean connect() {
		return gatt.connect();
	}

	@Override
	public void disconnect() {
		gatt.disconnect();
	}

	@Override
	public void close() {
		gatt.close();
	}

	@Override
	public boolean discoverServices() {
		return gatt.discoverServices();
	}

	@Nullable
	@Override
	public BluetoothGattService getService(@NonNull UUID uuid) {
		return gatt.getService(uuid);
	}

	@Override
	public boolean setCharacteristicNotification(@NonNull BluetoothGattCharacteristic characteristic, boolean enable) {
		return gatt.setCharacteristicNotification(characteristic, enable);
	}

	@Override
	public boolean readCharacteristic(@NonNull BluetoothGattCharacteristic characteristic) {
		return gatt.readCharacteristic(characteristic);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean writeCharacteristic(@NonNull BluetoothGattCharacteristic characteristic) {
		return gatt.writeCharacteristic(characteristic);
	}

	@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
	@Override
	public int writeCharacteristic(@NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int writeType) {
		return gatt.writeCharacteristic(characteristic, value, writeType);
	}

	@Override
	public boolean readDescriptor(@NonNull BluetoothGattDescriptor descriptor) {
		return gatt.readDescriptor(descriptor);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean writeDescriptor(@NonNull BluetoothGattDescriptor descriptor) {
		return gatt.writeDescriptor(descriptor);
	}

	@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
	@Override
	public int writeDescriptor(@NonNull BluetoothGattDescriptor descriptor, @NonNull byte[] value) {
		return gatt.writeDescriptor(descriptor, value);
	}

	@Override
	public boolean beginReliableWrite() {
		return gatt.beginReliableWrite();
	}

	@Override
	public boolean executeReliableWrite() {
		return gatt.executeReliableWrite();
	}

	@Override
	public void abortReliableWrite() {
		gatt.abortReliableWrite();
	}

	@Override
	public boolean requestMtu(int mtu) {
		return gatt.requestMtu(mtu);
	}

	@Override
	public boolean requestConnectionPriority(int priority) {
		return gatt.requestConnectionPriority(priority);
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	@Override
	public void setPreferredPhy(int txPhy, int rxPhy, int phyOptions) {
		gatt.setPreferredPhy(txPhy, rxPhy, phyOptions);
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	@Override
	public void readPhy() {
		gatt.readPhy();
	}

	@Override
	public boolean readRemoteRssi() {
		return gatt.readRemoteRssi();
	}
}

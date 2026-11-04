package no.nordicsemi.android.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.aaps.core.interfaces.pump.ble.BleTransport;
import app.aaps.core.interfaces.pump.ble.BleTransportListener;

/**
 * AAPS change to the Nordic library, TEMPORARY: a GATT connection on a {@link BleTransport}, so the vendor
 * AAR and this library run without a radio.
 *
 * Android delivers GATT events later on another thread, so events are posted to the library's handler and
 * never called directly. The final emulator hook is planned for the v4 driver.
 */
public final class EmulatorGattLink implements GattLink {

	private static final UUID SERVICE_PUMP = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
	private static final UUID CHARACTERISTIC_WRITE = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
	private static final UUID CHARACTERISTIC_NOTIFY = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
	private static final UUID SERVICE_DEVICE_INFO = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
	private static final UUID CHARACTERISTIC_FIRMWARE = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
	private static final UUID CHARACTERISTIC_HARDWARE = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb");
	private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
	private static final byte[] EMULATOR_VERSION = "emulator".getBytes(StandardCharsets.UTF_8);

	/** When set, every connection of the library goes here and not to the radio. */
	@Nullable
	private static volatile BleTransport transport;

	public static void activate(@Nullable BleTransport value) {
		transport = value;
	}

	public static boolean isActive() {
		return transport != null;
	}

	private final BluetoothDevice device;
	private final GattLinkCallback callback;
	private final Handler handler;
	private final BleTransport link;

	private final BluetoothGattCharacteristic notify = new BluetoothGattCharacteristic(
			CHARACTERISTIC_NOTIFY, BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ);
	private final BluetoothGattDescriptor notifyCccd = new BluetoothGattDescriptor(
			CCCD, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
	private final BluetoothGattCharacteristic write = new BluetoothGattCharacteristic(
			CHARACTERISTIC_WRITE,
			BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
			BluetoothGattCharacteristic.PERMISSION_WRITE);
	private final BluetoothGattCharacteristic firmware = new BluetoothGattCharacteristic(
			CHARACTERISTIC_FIRMWARE, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
	private final BluetoothGattCharacteristic hardware = new BluetoothGattCharacteristic(
			CHARACTERISTIC_HARDWARE, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
	private final BluetoothGattService pumpService = new BluetoothGattService(SERVICE_PUMP, BluetoothGattService.SERVICE_TYPE_PRIMARY);
	private final BluetoothGattService infoService = new BluetoothGattService(SERVICE_DEVICE_INFO, BluetoothGattService.SERVICE_TYPE_PRIMARY);

	private final BleTransportListener transportListener = new BleTransportListener() {
		@Override
		public void onConnectionStateChanged(boolean connected) {
			final int state = connected ? BluetoothProfile.STATE_CONNECTED : BluetoothProfile.STATE_DISCONNECTED;
			handler.post(() -> callback.onConnectionStateChange(EmulatorGattLink.this, BluetoothGatt.GATT_SUCCESS, state));
		}

		@Override
		public void onServicesDiscovered(boolean success) {
			final int status = success ? BluetoothGatt.GATT_SUCCESS : BluetoothGatt.GATT_FAILURE;
			handler.post(() -> callback.onServicesDiscovered(EmulatorGattLink.this, status));
		}

		@Override
		public void onDescriptorWritten() {
			handler.post(() -> callback.onDescriptorWrite(EmulatorGattLink.this, notifyCccd, BluetoothGatt.GATT_SUCCESS));
		}

		@Override
		public void onCharacteristicChanged(@NonNull byte[] data) {
			handler.post(() -> callback.onCharacteristicChanged(EmulatorGattLink.this, notify, data));
		}

		@Override
		public void onCharacteristicWritten() {
			handler.post(() -> callback.onCharacteristicWrite(EmulatorGattLink.this, write, BluetoothGatt.GATT_SUCCESS));
		}
	};

	EmulatorGattLink(@NonNull BluetoothDevice device, @NonNull GattLinkCallback callback, @NonNull Handler handler) {
		this.device = device;
		this.callback = callback;
		this.handler = handler;
		this.link = transport;
		notify.addDescriptor(notifyCccd);
		pumpService.addCharacteristic(notify);
		pumpService.addCharacteristic(write);
		infoService.addCharacteristic(firmware);
		infoService.addCharacteristic(hardware);
		link.setListener(transportListener);
	}

	/**
	 * Takes the place of the AAR's `isRequiredServiceSupported`, which needs a real {@link BluetoothGatt}.
	 * It only copies the characteristics into these public fields, so this sets them directly.
	 */
	boolean fillManagerCharacteristics(@NonNull BleManager manager) {
		try {
			set(manager, "b", notify);
			set(manager, "c", write);
			set(manager, "d", hardware);
			set(manager, "e", firmware);
			return true;
		} catch (final Exception e) {
			throw new IllegalStateException("The AAR does not have the expected fields", e);
		}
	}

	private static void set(BleManager manager, String name, BluetoothGattCharacteristic value) throws Exception {
		final Field field = manager.getClass().getField(name);
		field.set(manager, value);
	}

	@NonNull
	@Override
	public BluetoothDevice getDevice() {
		return device;
	}

	@Nullable
	@Override
	public BluetoothGatt realGatt() {
		return null;
	}

	@Override
	public boolean connect() {
		return link.getGatt().connect(device.getAddress());
	}

	@Override
	public void disconnect() {
		link.getGatt().disconnect();
	}

	@Override
	public void close() {
		link.getGatt().close();
		link.setListener(null);
	}

	@Override
	public boolean discoverServices() {
		link.getGatt().discoverServices();
		return true;
	}

	@Nullable
	@Override
	public BluetoothGattService getService(@NonNull UUID uuid) {
		if (SERVICE_PUMP.equals(uuid)) return pumpService;
		if (SERVICE_DEVICE_INFO.equals(uuid)) return infoService;
		return null;
	}

	@Override
	public boolean setCharacteristicNotification(@NonNull BluetoothGattCharacteristic characteristic, boolean enable) {
		return true;
	}

	@Override
	public boolean readCharacteristic(@NonNull BluetoothGattCharacteristic characteristic) {
		handler.post(() -> callback.onCharacteristicRead(this, characteristic, EMULATOR_VERSION, BluetoothGatt.GATT_SUCCESS));
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean writeCharacteristic(@NonNull BluetoothGattCharacteristic characteristic) {
		link.getGatt().writeCharacteristic(characteristic.getValue());
		return true;
	}

	@Override
	public int writeCharacteristic(@NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int writeType) {
		link.getGatt().writeCharacteristic(value);
		return 0; // BluetoothStatusCodes.SUCCESS
	}

	@Override
	public boolean readDescriptor(@NonNull BluetoothGattDescriptor descriptor) {
		return false;
	}

	@Override
	public boolean writeDescriptor(@NonNull BluetoothGattDescriptor descriptor) {
		link.getGatt().enableNotifications();
		return true;
	}

	@Override
	public int writeDescriptor(@NonNull BluetoothGattDescriptor descriptor, @NonNull byte[] value) {
		link.getGatt().enableNotifications();
		return 0; // BluetoothStatusCodes.SUCCESS
	}

	@Override
	public boolean beginReliableWrite() {
		return false;
	}

	@Override
	public boolean executeReliableWrite() {
		return false;
	}

	@Override
	public void abortReliableWrite() {
	}

	@Override
	public boolean requestMtu(int mtu) {
		handler.post(() -> callback.onMtuChanged(this, mtu, BluetoothGatt.GATT_SUCCESS));
		return true;
	}

	@Override
	public boolean requestConnectionPriority(int priority) {
		return true;
	}

	@Override
	public void setPreferredPhy(int txPhy, int rxPhy, int phyOptions) {
	}

	@Override
	public void readPhy() {
	}

	@Override
	public boolean readRemoteRssi() {
		return false;
	}
}

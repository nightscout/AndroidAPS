package info.nightscout.androidaps.plugins.PumpInsightLocal.utils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;

public class ConnectionEstablisher extends Thread {

    private Callback callback;
    private boolean forPairing;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothSocket socket;

    public ConnectionEstablisher(Callback callback, boolean forPairing, BluetoothAdapter bluetoothAdapter, BluetoothDevice bluetoothDevice, BluetoothSocket socket) {
        this.callback = callback;
        this.forPairing = forPairing;
        this.bluetoothAdapter = bluetoothAdapter;
        this.bluetoothDevice = bluetoothDevice;
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            if (!bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.enable();
                Thread.sleep(2000);
            }
        } catch (InterruptedException ignored) {
            return;
        }
        if (forPairing && bluetoothDevice.getBondState() != BluetoothDevice.BOND_NONE) {
            try {
                Method removeBond = bluetoothDevice.getClass().getMethod("removeBond", (Class[]) null);
                removeBond.invoke(bluetoothDevice, (Object[]) null);
            } catch (ReflectiveOperationException e) {
                if (!isInterrupted()) callback.onConnectionFail(e, 0);
                return;
            }
        }
        try {
            if (socket == null) {
                socket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"));
                callback.onSocketCreated(socket);
            }
        } catch (IOException e) {
            if (!isInterrupted()) callback.onConnectionFail(e, 0);
            return;
        }
        long connectionStart = System.currentTimeMillis();
        try {
            socket.connect();
            if (!isInterrupted()) callback.onConnectionSucceed();
        } catch (IOException e) {
            if (!isInterrupted()) callback.onConnectionFail(e, System.currentTimeMillis() - connectionStart);
        }
    }

    public void close(boolean closeSocket) {
        try {
            interrupt();
            if (closeSocket && socket != null && socket.isConnected()) socket.close();
        } catch (IOException ignored) {
        }
    }

    public interface Callback {
        void onSocketCreated(BluetoothSocket bluetoothSocket);

        void onConnectionSucceed();

        void onConnectionFail(Exception e, long duration);
    }
}

package org.monkey.d.ruffy.ruffy.driver;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;

/**
 * Created by fishermen21 on 15.05.17.
 */

class ListenThread implements Runnable {
    private final BluetoothServerSocket srvSock;

    public ListenThread(BluetoothServerSocket srvSock) {
        this.srvSock = srvSock;
    }
    public void run() {
        BluetoothSocket socket = null;

        try {
            if (socket != null) {
                socket = srvSock.accept();
            }
            if (socket != null) {
                socket.close();
                socket=null;
            }
        }
        catch(Exception e)
        {

        }
    }
    public void halt()
    {
        try {
            srvSock.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

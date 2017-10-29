package org.monkey.d.ruffy.ruffy.driver;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by SandraK82 on 15.05.17.
 */

public class BTConnection {
    private final BTHandler handler;
    private BluetoothAdapter bluetoothAdapter;
    private ListenThread listen;

    private BluetoothSocket currentConnection;

    public int seqNo;
    private InputStream currentInput;
    private OutputStream currentOutput;
    private PairingRequest pairingReciever;
    private ConnectReceiver connectReceiver;

    private PumpData pumpData;

    public BTConnection(final BTHandler handler)
    {
        this.handler = handler;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            handler.requestBlueTooth();
        }
    }

    public void makeDiscoverable(Activity activity) {

        this.pumpData = new PumpData(activity);

        IntentFilter filter = new IntentFilter("android.bluetooth.device.action.PAIRING_REQUEST");
        pairingReciever = new PairingRequest(activity, handler);
        activity.registerReceiver(pairingReciever, filter);

        Intent discoverableIntent = new Intent("android.bluetooth.adapter.action.REQUEST_DISCOVERABLE");
        discoverableIntent.putExtra("android.bluetooth.adapter.extra.DISCOVERABLE_DURATION", 60);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
        activity.startActivity(discoverableIntent);

        BluetoothServerSocket srvSock = null;
        try {
            srvSock = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("SerialLink", UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"));
        } catch (IOException e) {
            handler.fail("socket listen() failed");
            return;
        }

        final BluetoothServerSocket lSock = srvSock;
        listen = new ListenThread(srvSock);

        filter = new IntentFilter("android.bluetooth.device.action.ACL_CONNECTED");
        connectReceiver = new ConnectReceiver(handler);
        activity.registerReceiver(connectReceiver, filter);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool( 1 );
        scheduler.execute(listen);
    }

    public void stopDiscoverable() {
        if(listen!=null)
        {
            listen.halt();
        }
        if(bluetoothAdapter.isDiscovering())
        {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    public void connect(BluetoothDevice device) {
        connect(device.getAddress(), 4);
    }

    public void connect(PumpData pumpData, int retries)
    {
        this.pumpData = pumpData;
        connect(pumpData.getPumpMac(),retries);
    }

    private int state = 0;

    private void connect(String deviceAddress, int retry) {

        if(state!=0)
        {
            handler.log("in connect!");
            return;
        }
        state=1;
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);

        BluetoothSocket tmp = null;
        try {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            tmp = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"));
        } catch (IOException e) {
            handler.fail("socket create() failed: "+e.getMessage());
        }
        if(tmp != null) {
            stopDiscoverable();
            activateConnection(tmp);
        }
        else
        {
            handler.fail("failed the pump connection( retries left: "+retry+")");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(retry>0)
            {
                connect(deviceAddress,retry-1);
            }
            else
            {
                handler.fail("Failed to connect");
            }
        }
    }

    private void startReadThread() {
        new Thread() {
            @Override
            public void run() {
                try {
                    currentConnection.connect();//This method will block until a connection is made or the connection fails. If this method returns without an exception then this socket is now connected.
                    currentInput = currentConnection.getInputStream();
                    currentOutput = currentConnection.getOutputStream();
                } catch (IOException e) {
                    //e.printStackTrace();
                    handler.fail("no connection possible: " + e.getMessage());


                    //??????????
                    //state=1;
                    //return;
                }
                try {
                    pumpData.getActivity().unregisterReceiver(connectReceiver);
                }catch(Exception e){/*ignore*/}
                try {
                    pumpData.getActivity().unregisterReceiver(pairingReciever);
                }catch(Exception e){/*ignore*/}
                state=0;

                //here check if really connected!
                //this will start thread to write
                handler.deviceConnected();//in ruffy.java


                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                byte[] buffer = new byte[512];
                while (true) {
                    try {
                        int bytes = currentInput.read(buffer);
                        handler.log("read "+bytes+": "+ Utils.byteArrayToHexString(buffer,bytes));
                        handler.handleRawData(buffer,bytes);
                    } catch (Exception e) {
                        //e.printStackTrace();
                        //do not fail here as we maybe just closed the socket..
                        handler.log("got error in read");
                        return;
                    }
                }
            }
        }.start();
    }

    public void writeCommand(byte[] key) {
        List<Byte> out = new LinkedList<Byte>();
        for(Byte b : key)
            out.add(b);
        for (Byte n : pumpData.getNonceTx())
            out.add(n);
        Utils.addCRC(out);

        List<Byte> temp = Frame.frameEscape(out);

        byte[] ro = new byte[temp.size()];
        int i = 0;
        for(byte b : temp)
            ro[i++]=b;

        StringBuilder sb = new StringBuilder();
        for (i = 0; i < key.length; i++) {
            sb.append(String.format("%02X ", key[i]));
        }
        handler.log("writing command: "+sb.toString());
        write(ro);
    }

    private void activateConnection(BluetoothSocket newConnection){
        if(this.currentConnection!=null)
        {
            try {
                this.currentOutput.close();
            } catch (Exception e) {/*ignore*/}
            try {
                this.currentInput.close();
            } catch (Exception e) {/*ignore*/}
            try {
                this.currentConnection.close();
            } catch (Exception e) {/*ignore*/}
            this.currentInput=null;
            this.currentOutput=null;
            this.currentConnection=null;
            handler.fail("closed current Connection");
        }
        handler.fail("got new Connection: "+newConnection);
        this.currentConnection = newConnection;
        if(newConnection!=null)
        {
            startReadThread();
        }
    }

    public void write(byte[] ro){

        handler.log("!!!write!!!");

        if(this.currentConnection==null)
        {
            handler.fail("unable to write: no socket");
            return;
        }
        try {
            currentOutput.write(ro);
            handler.log("wrote "+ro.length+" bytes: "+ Utils.byteArrayToHexString(ro,ro.length));
        }catch(Exception e)
        {
            //e.printStackTrace();
            handler.fail("failed write of "+ro.length+" bytes!");
        }
    }

    public void log(String s) {
        if(handler!=null)
            handler.log(s);
    }

    public void disconnect() {
        try {
            this.currentOutput.close();
        } catch (Exception e) {/*ignore*/}
        try {
            this.currentInput.close();
        } catch (Exception e) {/*ignore*/}
        try {
            this.currentConnection.close();
        } catch (Exception e) {/*ignore*/}
        this.currentInput=null;
        this.currentOutput=null;
        this.currentConnection=null;
        this.pumpData = null;

        handler.log("disconnect() closed current Connection");
    }

    public PumpData getPumpData() {
        return pumpData;
    }

    public boolean isConnected() {
        return this.currentConnection != null && this.currentConnection.isConnected();
    }
}

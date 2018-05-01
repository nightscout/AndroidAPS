package com.gxwtech.roundtrip2;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.gxwtech.roundtrip2.ServiceData.ServiceCommand;
import com.gxwtech.roundtrip2.ServiceData.ServiceTransport;

import org.joda.time.DateTime;
import org.joda.time.Instant;

/**
 * Created by geoff on 6/11/16.
 */
public class RoundtripServiceClientConnection {
    private static final String TAG = "RTServiceClient";
    private Context context;
    private Messenger mService = null;
    private boolean mBound = false;

    public RoundtripServiceClientConnection(Context context) {
        this.context = context;
    }

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            Intent intent;
            switch (msg.what) {
                case RT2Const.IPC.MSG_clientRegistered:
                    // Service has registered us. Communication lines are open.
                    mBound = true;
                    intent = new Intent(RT2Const.local.INTENT_serviceConnected);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    break;
                case RT2Const.IPC.MSG_IPC:
                    // broadcast contents of message as an intent
                    ServiceTransport transport = new ServiceTransport(msg.getData());
                    Log.d(TAG,"Client received IPC message, bouncing to local: " + transport.describeContentsShort());
                    intent = new Intent(transport.getTransportType());
                    intent.putExtra(RT2Const.IPC.bundleKey,transport.getMap());
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    break;
                default:
                    Log.e(TAG,"handleMessage: unknown 'what' in message: "+msg.what);
                    super.handleMessage(msg);
            }
        }
    }

    final Messenger mMessenger = new Messenger(new IncomingHandler());

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            try {
                Message msg = Message.obtain(null,RT2Const.IPC.MSG_registerClient);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
            Log.d(TAG,"Sent registration message to service");
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mService = null;
            Log.d(TAG,"Disconnected from service.");
        }
    };

    public ServiceConnection getServiceConnection() {
        return mConnection;
    }

    public void unbind() {
        if (mBound) {
            if (mService!=null) {
                try {
                    Message msg = Message.obtain(null,RT2Const.IPC.MSG_unregisterClient);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // Nothing to do if the connection has already crashed.
                }
            }
            mBound = false;
        }
    }

    public boolean sendServiceCommand(ServiceCommand command) {
        if (!mBound) {
            Log.e(TAG,"sendServiceCommand: cannot send command -- not yet bound to service");
            return false;
        }

        ServiceTransport transport = new ServiceTransport();
        Log.d(TAG,"client sending message: " + transport.describeContentsShort());

        // can't set sender hashcode -- Service will do that.
        transport.setServiceCommand(command);
        transport.setTransportType(RT2Const.IPC.MSG_ServiceCommand);

        Message msg = Message.obtain(null, RT2Const.IPC.MSG_IPC, 0, 0);

        msg.setData(transport.getMap());
        msg.replyTo = mMessenger;
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            Log.e(TAG,"sendServiceCommand: failed to send message");
            e.printStackTrace();
            return false;
        }
        return true;
    }
}

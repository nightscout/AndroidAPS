package info.nightscout.androidaps.plugins.pump.danaR.services;

import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase;

/**
 * Created by mike on 28.01.2018.
 */

public abstract class AbstractSerialIOThread extends Thread {

    public abstract void sendMessage(MessageBase message);
    public abstract void disconnect(String reason);
}

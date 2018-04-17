package info.nightscout.androidaps.plugins.PumpDanaR.services;

import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;

/**
 * Created by mike on 28.01.2018.
 */

public abstract class AbstractSerialIOThread extends Thread {

    public abstract void sendMessage(MessageBase message);
    public abstract void disconnect(String reason);
}

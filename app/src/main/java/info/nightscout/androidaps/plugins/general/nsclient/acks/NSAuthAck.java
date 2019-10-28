package info.nightscout.androidaps.plugins.general.nsclient.acks;

import org.json.JSONObject;

import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.plugins.bus.RxBus;
import io.socket.client.Ack;

public class NSAuthAck extends Event implements Ack{
    public boolean read = false;
    public boolean write = false;
    public boolean write_treatment = false;

    public void call(Object...args) {
        JSONObject response = (JSONObject)args[0];
        read = response.optBoolean("read");
        write = response.optBoolean("write");
        write_treatment = response.optBoolean("write_treatment");
        RxBus.INSTANCE.send(this);
    }
}

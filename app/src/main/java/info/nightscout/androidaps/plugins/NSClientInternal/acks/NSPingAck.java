package info.nightscout.androidaps.plugins.NSClientInternal.acks;

import org.json.JSONException;
import org.json.JSONObject;

import io.socket.client.Ack;

/**
 * Created by mike on 29.12.2015.
 */
public class NSPingAck  implements Ack {
    public long mills = 0;
    public boolean received = false;
    public boolean auth_received = false;
    public boolean read = false;
    public boolean write = false;
    public boolean write_treatment = false;

    public void call(Object...args) {
        JSONObject response = (JSONObject)args[0];
        mills = response.optLong("mills");
        if (response.has("authorization")) {
            auth_received = true;
            try {
                JSONObject authorization = response.getJSONObject("authorization");
                read = authorization.optBoolean("read");
                write = authorization.optBoolean("write");
                write_treatment = authorization.optBoolean("write_treatment");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        received = true;
        synchronized(this) {
            this.notify();
        }
    }
}

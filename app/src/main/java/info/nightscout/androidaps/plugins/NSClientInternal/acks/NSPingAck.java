package info.nightscout.androidaps.plugins.NSClientInternal.acks;

import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.socket.client.Ack;

/**
 * Created by mike on 29.12.2015.
 */
public class NSPingAck  implements Ack {
    private static Logger log = LoggerFactory.getLogger(NSPingAck.class);

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
                log.error("Unhandled exception", e);
            }
        }
        received = true;
        synchronized(this) {
            this.notify();
        }
    }
}

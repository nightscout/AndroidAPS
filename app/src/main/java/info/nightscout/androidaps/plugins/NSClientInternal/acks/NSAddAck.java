package info.nightscout.androidaps.plugins.NSClientInternal.acks;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.NSClientInternal.services.NSClientService;
import io.socket.client.Ack;

/**
 * Created by mike on 29.12.2015.
 */
public class NSAddAck implements Ack {
    private static Logger log = LoggerFactory.getLogger(NSAddAck.class);
    public String _id = null;
    public void call(Object...args) {
        // Regular response
        try {
            JSONArray responsearray = (JSONArray) (args[0]);
            JSONObject response = null;
            if (responsearray.length()>0) {
                    response = responsearray.getJSONObject(0);
                _id = response.getString("_id");
            }
            synchronized(this) {
                this.notify();
            }
            return;
        } catch (Exception e) {
        }
        // Check for not authorized
        try {
            JSONObject response = (JSONObject) (args[0]);
            if (response.has("result")) {
                _id = null;
                if (response.getString("result").equals("Not authorized")) {
                    synchronized(this) {
                        this.notify();
                    }
                    NSClientService.forcerestart = true;
                    return;
                }
                log.debug("DBACCESS " + response.getString("result"));
            }
            synchronized(this) {
                this.notify();
            }
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
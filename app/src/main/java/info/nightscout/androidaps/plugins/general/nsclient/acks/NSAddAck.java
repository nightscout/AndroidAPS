package info.nightscout.androidaps.plugins.general.nsclient.acks;

import org.json.JSONArray;
import org.json.JSONObject;

import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart;
import io.socket.client.Ack;

/**
 * Created by mike on 29.12.2015.
 */
public class NSAddAck extends Event implements Ack {
    private final AAPSLogger aapsLogger;
    private final RxBusWrapper rxBus;

    public String _id = null;
    public String nsClientID = null;
    public JSONObject json = null;

    public NSAddAck(AAPSLogger aapsLogger, RxBusWrapper rxBus) {
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
    }

    public void call(Object... args) {
        // Regular response
        try {
            JSONArray responsearray = (JSONArray) (args[0]);
            JSONObject response;
            if (responsearray.length() > 0) {
                response = responsearray.getJSONObject(0);
                _id = response.getString("_id");
                json = response;
                if (response.has("NSCLIENT_ID")) {
                    nsClientID = response.getString("NSCLIENT_ID");
                }
            }
            rxBus.send(this);
            return;
        } catch (Exception e) {
            aapsLogger.error("Unhandled exception", e);
        }
        // Check for not authorized
        try {
            JSONObject response = (JSONObject) (args[0]);
            if (response.has("result")) {
                _id = null;
                if (response.getString("result").contains("Not")) {
                    rxBus.send(new EventNSClientRestart());
                    return;
                }
                aapsLogger.debug(LTag.NSCLIENT, "DBACCESS " + response.getString("result"));
            }
        } catch (Exception e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }
}
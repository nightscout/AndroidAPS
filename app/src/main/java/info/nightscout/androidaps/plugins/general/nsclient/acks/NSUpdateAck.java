package info.nightscout.androidaps.plugins.general.nsclient.acks;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBus;
import io.socket.client.Ack;

/**
 * Created by mike on 21.02.2016.
 */
public class NSUpdateAck extends Event implements Ack {
    private final AAPSLogger aapsLogger;
    public boolean result = false;
    public String _id;
    public String action;

    public void call(Object... args) {
        JSONObject response = (JSONObject) args[0];
        if (response.has("result"))
            try {
                if (response.getString("result").equals("success"))
                    result = true;
                else if (response.getString("result").equals("Missing _id")) {
                    result = true;
                    aapsLogger.debug(LTag.NSCLIENT, "Internal error: Missing _id returned on dbUpdate ack");
                }
                RxBus.Companion.getINSTANCE().send(this);
            } catch (JSONException e) {
                aapsLogger.error("Unhandled exception", e);
            }
    }

    public NSUpdateAck(String action, String _id, AAPSLogger aapsLogger) {
        super();
        this.action = action;
        this._id = _id;
        this.aapsLogger = aapsLogger;
    }
}

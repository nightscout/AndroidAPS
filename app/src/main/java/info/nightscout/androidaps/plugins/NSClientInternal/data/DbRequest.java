package info.nightscout.androidaps.plugins.NSClientInternal.data;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

import org.json.JSONObject;

/**
 * Created by mike on 27.02.2016.
 *
 * Allowed actions "dbAdd" || "dbUpdate" || "dbUpdateUnset" || "dbRemove"
 */
public class DbRequest {
    public String action = null;
    public String collection = null;
    public JSONObject data = null;
    public String _id = null;

    // dbAdd
    public DbRequest(String action, String collection, JSONObject data) {
        this.action = action;
        this.collection = collection;
        this.data = data;
        this._id = "";
    }

    // dbUpdate, dbUpdateUnset
    public DbRequest(String action, String collection, String _id, JSONObject data) {
        this.action = action;
        this.collection = collection;
        this.data = data;
        this._id = _id;
    }

    // dbRemove
    public DbRequest(String action, String collection, String _id) {
        this.action = action;
        this.collection = collection;
        this.data = new JSONObject();
        this._id = _id;
    }

    public String hash() {
        return Hashing.sha1().hashString(action + collection + _id + data.toString(), Charsets.UTF_8).toString();
    }
}

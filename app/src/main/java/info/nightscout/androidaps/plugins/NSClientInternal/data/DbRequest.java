package info.nightscout.androidaps.plugins.NSClientInternal.data;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by mike on 27.02.2016.
 * <p>
 * Allowed actions "dbAdd" || "dbUpdate" || "dbUpdateUnset" || "dbRemove"
 */
public class DbRequest {
    public String action = null;
    public String collection = null;
    public JSONObject data = null;
    public String _id = null;
    public String nsClientID = null;

    public DbRequest() {
    }

    // dbAdd
    public DbRequest(String action, String collection, String nsClientID, JSONObject data) {
        this.action = action;
        this.collection = collection;
        this.data = data;
        this.nsClientID = nsClientID;
        this._id = "";
    }

    // dbUpdate, dbUpdateUnset
    public DbRequest(String action, String collection, String nsClientID, String _id, JSONObject data) {
        this.action = action;
        this.collection = collection;
        this.data = data;
        this.nsClientID = nsClientID;
        this._id = _id;
    }

    // dbRemove
    public DbRequest(String action, String collection, String nsClientID, String _id) {
        this.action = action;
        this.collection = collection;
        this.data = new JSONObject();
        this.nsClientID = nsClientID;
        this._id = _id;
    }

    public String hash() {
        return Hashing.sha1().hashString(action + collection + _id + data.toString(), Charsets.UTF_8).toString();
    }

    public JSONObject toJSON() {
        JSONObject object = new JSONObject();
        try {
            object.put("action", action);
            object.put("collection", collection);
            object.put("data", data);
            if (_id != null) object.put("_id", _id);
            if (nsClientID != null) object.put("nsClientID", nsClientID);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return object;
    }

    public static DbRequest fromJSON(JSONObject jsonObject) {
        DbRequest result = new DbRequest();
        try {
            if (jsonObject.has("action"))
                result.action = jsonObject.getString("action");
            if (jsonObject.has("collection"))
                result.collection = jsonObject.getString("collection");
            if (jsonObject.has("data"))
                result.data = jsonObject.getJSONObject("data");
            if (jsonObject.has("_id"))
                result._id = jsonObject.getString("_id");
            if (jsonObject.has("nsClientID"))
                result.nsClientID = jsonObject.getString("nsClientID");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }
}

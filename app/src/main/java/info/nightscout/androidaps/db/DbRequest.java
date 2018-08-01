package info.nightscout.androidaps.db;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

/**
 * Created by mike on 27.02.2016.
 * <p>
 * Allowed actions "dbAdd" || "dbUpdate" || "dbUpdateUnset" || "dbRemove"
 */
@DatabaseTable(tableName = DatabaseHelper.DATABASE_DBREQUESTS)
public class DbRequest {
    private static Logger log = LoggerFactory.getLogger(L.DATABASE);

    @DatabaseField(id = true)
    public String nsClientID = null;

    @DatabaseField
    public String action = null;

    @DatabaseField
    public String collection = null;

    @DatabaseField
    public String data = null;

    @DatabaseField
    public String _id = null;

    public DbRequest() {
    }

    // dbAdd
    public DbRequest(String action, String collection, String nsClientID, JSONObject data) {
        this.action = action;
        this.collection = collection;
        this.data = data.toString();
        this.nsClientID = nsClientID;
        this._id = "";
    }

    // dbUpdate, dbUpdateUnset
    public DbRequest(String action, String collection, String nsClientID, String _id, JSONObject data) {
        this.action = action;
        this.collection = collection;
        this.data = data.toString();
        this.nsClientID = nsClientID;
        this._id = _id;
    }

    // dbRemove
    public DbRequest(String action, String collection, String nsClientID, String _id) {
        this.action = action;
        this.collection = collection;
        this.data = new JSONObject().toString();
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
            object.put("data", new JSONObject(data));
            if (_id != null) object.put("_id", _id);
            if (nsClientID != null) object.put("nsClientID", nsClientID);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
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
                result.data = jsonObject.getJSONObject("data").toString();
            if (jsonObject.has("_id"))
                result._id = jsonObject.getString("_id");
            if (jsonObject.has("nsClientID"))
                result.nsClientID = jsonObject.getString("nsClientID");
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return result;
    }
}

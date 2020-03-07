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
import info.nightscout.androidaps.utils.DateUtil;

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
    public DbRequest(String action, String collection, JSONObject json) {
        this.action = action;
        this.collection = collection;
        this.nsClientID = "" + DateUtil.now();
        try {
            json.put("NSCLIENT_ID", nsClientID);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.data = json.toString();
        this._id = "";
    }

    // dbUpdate, dbUpdateUnset
    public DbRequest(String action, String collection, String _id, JSONObject json) {
        this.action = action;
        this.collection = collection;
        this.nsClientID = "" + DateUtil.now();
        try {
            json.put("NSCLIENT_ID", nsClientID);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.data = json.toString();
        this._id = _id;
    }

    // dbRemove
    public DbRequest(String action, String collection,
                     String _id) {
        JSONObject json = new JSONObject();
        this.action = action;
        this.collection = collection;
        this.nsClientID = "" + DateUtil.now();
        try {
            json.put("NSCLIENT_ID", nsClientID);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.data = json.toString();
        this._id = _id;
    }

    public String log() {
        return
                "\nnsClientID:" + nsClientID +
                "\naction:" + action +
                "\ncollection:" + collection +
                "\ndata:" + data +
                "\n_id:" + _id;
    }
}

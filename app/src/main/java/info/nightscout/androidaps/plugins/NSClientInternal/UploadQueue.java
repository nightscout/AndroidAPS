package info.nightscout.androidaps.plugins.NSClientInternal;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.NSClientInternal.broadcasts.BroadcastQueueStatus;
import info.nightscout.androidaps.plugins.NSClientInternal.data.DbRequest;
import info.nightscout.androidaps.plugins.NSClientInternal.services.NSClientService;
import info.nightscout.utils.SP;

/**
 * Created by mike on 21.02.2016.
 */
public class UploadQueue {
    private static Logger log = LoggerFactory.getLogger(UploadQueue.class);

    public static HashMap<String, DbRequest> queue = null;

    public UploadQueue() {
        loadMap();
    }

    public static String status() {
        return "QUEUE: " + queue.size();
    }

    public static int size() {
        return queue.size();
    }

    public static void add(final DbRequest dbr) {
        NSClientService.handler.post(new Runnable() {
            @Override
            public void run() {
                log.debug("QUEUE adding: " + dbr.data.toString());
                queue.put(dbr.hash(), dbr);
                saveMap();
            }
        });
    }

    public static void reset() {
        NSClientService.handler.post(new Runnable() {
            @Override
            public void run() {
                log.debug("QUEUE Reset");
                queue.clear();
                log.debug(status());
            }
        });
    }

    public static void removeID(final JSONObject record) {
        NSClientService.handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    long id = -1L;
                    if (record.has("NSCLIENT_ID")) {
                        id = record.getLong("NSCLIENT_ID");
                    } else {
                        return;
                    }
                    Iterator<Map.Entry<String, DbRequest>> iter = queue.entrySet().iterator();
                    while (iter.hasNext()) {
                        DbRequest dbr = iter.next().getValue();
                        JSONObject data = dbr.data;
                        long nsclientId = -1;
                        if (data.has("NSCLIENT_ID")) {
                            nsclientId = data.getLong("NSCLIENT_ID");
                            if (nsclientId == id) {
                                log.debug("Removing item from UploadQueue");
                                iter.remove();
                                log.debug(UploadQueue.status());
                                return;
                            }
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                saveMap();
            }
        });
    }

    public static void removeID(final String action, final String _id) {
        NSClientService.handler.post(new Runnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<String, DbRequest>> iter = queue.entrySet().iterator();
                while (iter.hasNext()) {
                    DbRequest dbr = iter.next().getValue();
                    if (dbr.action.equals(action) && dbr._id.equals(_id)) {
                        log.debug("Removing item from UploadQueue");
                        iter.remove();
                        return;
                    } else {
                        log.debug("Failed removing item from UploadQueue");
                    }
                }
                saveMap();
            }
        });
    }

    final static String KEY = "UploadQueue";

    private static void saveMap() {
        JSONArray jsonArray = new JSONArray();
        Iterator<Map.Entry<String, DbRequest>> iter = queue.entrySet().iterator();
        while (iter.hasNext()) {
            DbRequest dbr = iter.next().getValue();
            jsonArray.put(dbr.toJSON());
        }
        SP.putString(KEY, jsonArray.toString());
    }

    private void loadMap() {
        queue = new HashMap<String, DbRequest>();
        try {
            String jsonString = SP.getString(KEY, (new JSONArray()).toString());
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i=0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                DbRequest dbr = DbRequest.fromJSON(jsonObject);
                queue.put(dbr.hash(), dbr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String textList() {
        Iterator<Map.Entry<String, DbRequest>> iter = queue.entrySet().iterator();
        String result = "";

        while (iter.hasNext()) {
            DbRequest dbr = iter.next().getValue();
            result += "<br>";
            result += dbr.action.toUpperCase() + " ";
            result += dbr.collection + ": ";
            result += dbr.data.toString();
        }
        return result;
    }

}

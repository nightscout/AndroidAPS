package info.nightscout.androidaps.plugins.NSClientInternal;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.NSClientInternal.acks.NSAddAck;
import info.nightscout.androidaps.plugins.NSClientInternal.acks.NSUpdateAck;
import info.nightscout.androidaps.plugins.NSClientInternal.broadcasts.BroadcastQueueStatus;
import info.nightscout.androidaps.plugins.NSClientInternal.data.DbRequest;
import info.nightscout.androidaps.plugins.NSClientInternal.services.NSClientService;

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
            }
        });
    }

    public static void put(final String hash, final DbRequest dbr) {
        NSClientService.handler.post(new Runnable() {
            @Override
            public void run() {
                queue.put(hash, dbr);
                BroadcastQueueStatus bs = new BroadcastQueueStatus();
                bs.handleNewStatus(queue.size(), MainApp.instance().getApplicationContext());
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
            }
        });
    }

    final String KEY = "UploadQueue";

    private void saveMap() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        JSONObject jsonObject = new JSONObject(queue);
        String jsonString = jsonObject.toString();
        SharedPreferences.Editor editor = sp.edit();
        editor.remove(KEY).commit();
        editor.putString(KEY, jsonString);
        editor.commit();
    }

    private void loadMap() {
        queue = new HashMap<String, DbRequest>();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        try {
            String jsonString = sp.getString(KEY, (new JSONObject()).toString());
            JSONObject jsonObject = new JSONObject(jsonString);
            Iterator<String> keysItr = jsonObject.keys();
            while (keysItr.hasNext()) {
                String key = keysItr.next();
                DbRequest value = (DbRequest) jsonObject.get(key);
                queue.put(key, value);
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
            result += dbr.action.toUpperCase() + " ";
            result += dbr.collection + ": ";
            result += dbr.data.toString();
        }
        return result;
    }

}

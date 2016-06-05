package info.nightscout.client.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.client.broadcasts.Intents;
import info.nightscout.client.data.NSProfile;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.Treatment;

public class NSClientDataReceiver extends BroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(NSClientDataReceiver.class);
    public NSClientDataReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundles = intent.getExtras();
        if (bundles == null) return;


        // Handle profile
        if (intent.getAction().equals(Intents.ACTION_NEW_PROFILE)){
            try {
                String activeProfile = bundles.getString("activeprofile");
                String profile = bundles.getString("profile");
                NSProfile nsProfile = new NSProfile(new JSONObject(profile), activeProfile);
                MainApp.instance().setNSProfile(nsProfile);
                MainApp.instance().setActiveProfile(activeProfile);
                storeNSProfile();
                if (MainApp.getActivePump() != null) {
                    MainApp.getActivePump().setNewBasalProfile();
                } else {
                    log.error("No active pump selected");
                }
                log.debug("Received profile: " + activeProfile + " " + profile);
                MainApp.bus().post(new EventNewBasalProfile());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (intent.getAction().equals(Intents.ACTION_NEW_TREATMENT)) {
            try {
                String trstring = bundles.getString("treatment");
                JSONObject trJson = new JSONObject(trstring);
                if (!trJson.has("insulin") && !trJson.has("carbs")) {
                    log.debug("ADD: Uninterested treatment: " + trstring);
                    return;
                }

                Treatment stored = null;
                trJson = new JSONObject(trstring);
                String _id = trJson.getString("_id");

                if (trJson.has("timeIndex")) {
                    log.debug("ADD: timeIndex found: " + trstring);
                    stored = findByTimeIndex(trJson.getLong("timeIndex"));
                } else {
                    stored = findById(_id);
                }

                if (stored != null) {
                    log.debug("ADD: Existing treatment: " + trstring);
                    if (trJson.has("timeIndex")) {
                        stored._id = _id;
                        MainApp.getDbHelper().getDaoTreatments().update(stored);
                    }
                    return;
                } else {
                    log.debug("ADD: New treatment: " + trstring);
                    Treatment treatment = new Treatment();
                    treatment._id = _id;
                    treatment.carbs = trJson.has("carbs") ? trJson.getDouble("carbs") : 0;
                    treatment.insulin = trJson.has("insulin") ? trJson.getDouble("insulin") : 0d;
                    treatment.created_at = new Date(trJson.getLong("mills"));
                    treatment.setTimeIndex(treatment.getTimeIndex());
                    try {
                        MainApp.getDbHelper().getDaoTreatments().create(treatment);
                        log.debug("ADD: Stored treatment: " + treatment.log());
                        MainApp.bus().post(new EventTreatmentChange());
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception e1) {
                e1.printStackTrace();
            }

        }

        if (intent.getAction().equals(Intents.ACTION_CHANGED_TREATMENT)) {
            try {
                String trstring = bundles.getString("treatment");
                JSONObject trJson = new JSONObject(trstring);
                if (!trJson.has("insulin") && !trJson.has("carbs")) {
                    log.debug("CHANGE: Uninterested treatment: " + trstring);
                    return;
                }
                trJson = new JSONObject(trstring);
                String _id = trJson.getString("_id");

                Treatment stored;

                if (trJson.has("timeIndex")) {
                    log.debug("ADD: timeIndex found: " + trstring);
                    stored = findByTimeIndex(trJson.getLong("timeIndex"));
                } else {
                    stored = findById(_id);
                }

                if (stored != null) {
                    log.debug("CHANGE: Existing treatment: " + trstring);
                    stored._id = _id;
                    stored.carbs = trJson.has("carbs") ? trJson.getDouble("carbs") : 0;
                    stored.insulin = trJson.has("insulin") ? trJson.getDouble("insulin") : 0d;
                    stored.created_at = new Date(trJson.getLong("mills"));
                    MainApp.getDbHelper().getDaoTreatments().update(stored);
                    MainApp.bus().post(new EventTreatmentChange());
                } else {
                    log.debug("CHANGE: New treatment: " + trstring);
                    Treatment treatment = new Treatment();
                    treatment._id = _id;
                    treatment.carbs = trJson.has("carbs") ? trJson.getDouble("carbs") : 0;
                    treatment.insulin = trJson.has("insulin") ? trJson.getDouble("insulin") : 0d;
                    //treatment.created_at = DateUtil.fromISODateString(trJson.getString("created_at"));
                    treatment.created_at = new Date(trJson.getLong("mills"));
                    treatment.setTimeIndex(treatment.getTimeIndex());
                    try {
                        MainApp.getDbHelper().getDaoTreatments().create(treatment);
                        log.debug("CHANGE: Stored treatment: " + treatment.log());
                        MainApp.bus().post(new EventTreatmentChange());
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }

        if (intent.getAction().equals(Intents.ACTION_REMOVED_TREATMENT)) {
            try {
                if (bundles.containsKey("treatment")) {
                    String trstring = bundles.getString("treatment");
                    JSONObject trJson = new JSONObject(trstring);
                    String _id = trJson.getString("_id");
                    removeTreatmentFromDb(_id);
                }

                if (bundles.containsKey("treatments")) {
                    String trstring = bundles.getString("treatments");
                    JSONArray jsonArray = new JSONArray(trstring);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject trJson = jsonArray.getJSONObject(i);
                        String _id = trJson.getString("_id");
                        removeTreatmentFromDb(_id);
                    }
                }
                MainApp.bus().post(new EventTreatmentChange());

            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }

        if (intent.getAction().equals(Intents.ACTION_NEW_SGV)) {
            MainApp.bus().post(new EventNewBG());
        }
    }

    public void storeNSProfile() {
        SharedPreferences settings = MainApp.instance().getApplicationContext().getSharedPreferences(MainApp.instance().PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("profile", MainApp.instance().getNSProfile().getData().toString());
        editor.putString("activeProfile", MainApp.instance().getActiveProfile());
        editor.commit();
    }

    public static Treatment findById(String _id) {
        try {
            QueryBuilder<Treatment, String> qb = null;
            Dao<Treatment, Long> daoTreatments = MainApp.getDbHelper().getDaoTreatments();
            QueryBuilder<Treatment, Long> queryBuilder = daoTreatments.queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            queryBuilder.limit(10);
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            List<Treatment> trList = daoTreatments.query(preparedQuery);
            if (trList.size() != 1) {
                //log.debug("Treatment findById query size: " + trList.size());
                return null;
            } else {
                //log.debug("Treatment findById found: " + trList.get(0).log());
                return trList.get(0);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Treatment findByTimeIndex(Long timeIndex) {
        try {
            QueryBuilder<Treatment, String> qb = null;
            Dao<Treatment, Long> daoTreatments = MainApp.getDbHelper().getDaoTreatments();
            QueryBuilder<Treatment, Long> queryBuilder = daoTreatments.queryBuilder();
            Where where = queryBuilder.where();
            where.eq("timeIndex", timeIndex);
            queryBuilder.limit(10);
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            List<Treatment> trList = daoTreatments.query(preparedQuery);
            if (trList.size() != 1) {
                log.debug("Treatment findByTimeIndex query size: " + trList.size());
                return null;
            } else {
                log.debug("Treatment findByTimeIndex found: " + trList.get(0).log());
                return trList.get(0);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void removeTreatmentFromDb(String _id) throws SQLException {
        Treatment stored = findById(_id);
        if (stored != null) {
            log.debug("REMOVE: Existing treatment (removing): " + _id);
            MainApp.getDbHelper().getDaoTreatments().delete(stored);
            MainApp.bus().post(new EventTreatmentChange());
        } else {
            log.debug("REMOVE: Not stored treatment (ignoring): " + _id);
        }
    }
}

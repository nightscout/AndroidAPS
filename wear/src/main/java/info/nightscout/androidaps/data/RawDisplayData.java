package info.nightscout.androidaps.data;

import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;

import com.google.android.gms.wearable.DataMap;

import java.util.ArrayList;
import java.util.Iterator;

import info.nightscout.androidaps.interaction.utils.Constants;
import info.nightscout.androidaps.interaction.utils.Persistence;
import info.nightscout.androidaps.interaction.utils.WearUtil;

/**
 * Holds bunch of data model variables and lists that arrive from phone app and are due to be
 * displayed on watchface and complications. Keeping them together makes code cleaner and allows
 * passing it to complications via persistence layer.
 *
 * Created by dlvoy on 2019-11-12
 */
public class RawDisplayData {

    static final String DATA_PERSISTENCE_KEY = "raw_data";
    static final String BASALS_PERSISTENCE_KEY = "raw_basals";
    static final String STATUS_PERSISTENCE_KEY = "raw_status";

    // data bundle
    public long sgvLevel = 0;
    public long datetime;
    public String sSgv = "---";
    public String sDirection  = "--";
    public String sDelta = "--";
    public String sAvgDelta = "--";
    public String sUnits = "-";

    // status bundle
    public String sBasalRate  = "-.--U/h";
    public String sUploaderBattery = "--";
    public String sRigBattery = "--";
    public boolean detailedIOB = false;
    public String sIOB1 = "IOB";
    public String sIOB2 = "-.--";
    public String sCOB1  = "Carb";
    public String sCOB2= "--g";
    public String sBgi = "--";
    public boolean showBGI = false;
    public String externalStatusString = "no status";
    public int batteryLevel = 1;
    public long openApsStatus = -1;

    // basals bundle
    public ArrayList<BgWatchData> bgDataList = new ArrayList<>();
    public ArrayList<TempWatchData> tempWatchDataList = new ArrayList<>();
    public ArrayList<BasalWatchData> basalWatchDataList = new ArrayList<>();
    public ArrayList<BolusWatchData> bolusWatchDataList = new ArrayList<>();
    public ArrayList<BgWatchData> predictionList = new ArrayList<>();

    public String toDebugString() {
        return "DisplayRawData{" +
                "sgvLevel=" + sgvLevel +
                ", datetime=" + datetime +
                ", sSgv='" + sSgv + '\'' +
                ", sDirection='" + sDirection + '\'' +
                ", sDelta='" + sDelta + '\'' +
                ", sAvgDelta='" + sAvgDelta + '\'' +
                ", sUnits='" + sUnits + '\'' +
                ", sBasalRate='" + sBasalRate + '\'' +
                ", sUploaderBattery='" + sUploaderBattery + '\'' +
                ", sRigBattery='" + sRigBattery + '\'' +
                ", detailedIOB=" + detailedIOB +
                ", sIOB1='" + sIOB1 + '\'' +
                ", sIOB2='" + sIOB2 + '\'' +
                ", sCOB1='" + sCOB1 + '\'' +
                ", sCOB2='" + sCOB2 + '\'' +
                ", sBgi='" + sBgi + '\'' +
                ", showBGI=" + showBGI +
                ", externalStatusString='" + externalStatusString + '\'' +
                ", batteryLevel=" + batteryLevel +
                ", openApsStatus=" + openApsStatus +
                ", bgDataList size=" + bgDataList.size() +
                ", tempWatchDataList size=" + tempWatchDataList.size() +
                ", basalWatchDataList size=" + basalWatchDataList.size() +
                ", bolusWatchDataLis size=" + bolusWatchDataList.size() +
                ", predictionList size=" + predictionList.size() +
                '}';
    }

    public void updateFromPersistence(Persistence persistence) {

        DataMap dataMapData = persistence.getDataMap(DATA_PERSISTENCE_KEY);
        if (dataMapData != null) {
            updateData(dataMapData);
        }
        DataMap dataMapStatus = persistence.getDataMap(STATUS_PERSISTENCE_KEY);
        if (dataMapStatus != null) {
            updateStatus(dataMapStatus);
        }
        DataMap dataMapBasals = persistence.getDataMap(BASALS_PERSISTENCE_KEY);
        if (dataMapBasals != null) {
            updateBasals(dataMapBasals);
        }
    }

    /*
     * Since complications do not need Basals, we skip them for performance
     */
    public void updateForComplicationsFromPersistence(Persistence persistence) {

        DataMap dataMapData = persistence.getDataMap(DATA_PERSISTENCE_KEY);
        if (dataMapData != null) {
            updateData(dataMapData);
        }
        DataMap dataMapStatus = persistence.getDataMap(STATUS_PERSISTENCE_KEY);
        if (dataMapStatus != null) {
            updateStatus(dataMapStatus);
        }
    }

    public DataMap updateDataFromMessage(Intent intent, PowerManager.WakeLock wakeLock) {
        Bundle bundle = intent.getBundleExtra("data");
        if (bundle != null) {
            DataMap dataMap = WearUtil.bundleToDataMap(bundle);
            updateData(dataMap);
            return dataMap;
        }
        return null;
    }

    private void updateData(DataMap dataMap) {
        WearUtil.getWakeLock("readingPrefs", 50);
        sgvLevel = dataMap.getLong("sgvLevel");
        datetime = dataMap.getLong("timestamp");
        sSgv = dataMap.getString("sgvString");
        sDirection = dataMap.getString("slopeArrow");
        sDelta = dataMap.getString("delta");
        sAvgDelta = dataMap.getString("avgDelta");
        sUnits = dataMap.getString("glucoseUnits");
    }

    public DataMap updateStatusFromMessage(Intent intent, PowerManager.WakeLock wakeLock) {
        Bundle bundle = intent.getBundleExtra("status");
        if (bundle != null) {
            DataMap dataMap = WearUtil.bundleToDataMap(bundle);
            updateStatus(dataMap);
            return dataMap;
        }
        return null;
    }

    private void updateStatus(DataMap dataMap) {
        WearUtil.getWakeLock("readingPrefs", 50);
        sBasalRate = dataMap.getString("currentBasal");
        sUploaderBattery = dataMap.getString("battery");
        sRigBattery = dataMap.getString("rigBattery");
        detailedIOB = dataMap.getBoolean("detailedIob");
        sIOB1 = dataMap.getString("iobSum") + "U";
        sIOB2 = dataMap.getString("iobDetail");
        sCOB1 = "Carb";
        sCOB2 = dataMap.getString("cob");
        sBgi = dataMap.getString("bgi");
        showBGI = dataMap.getBoolean("showBgi");
        externalStatusString = dataMap.getString("externalStatusString");
        batteryLevel = dataMap.getInt("batteryLevel");
        openApsStatus = dataMap.getLong("openApsStatus");
    }

    public DataMap updateBasalsFromMessage(Intent intent, PowerManager.WakeLock wakeLock) {
        Bundle bundle = intent.getBundleExtra("basals");
        if (bundle != null) {
            DataMap dataMap = WearUtil.bundleToDataMap(bundle);
            updateBasals(dataMap);
            return dataMap;
        }
        return null;
    }

    private void updateBasals(DataMap dataMap) {
        WearUtil.getWakeLock("readingPrefs", 500);
        loadBasalsAndTemps(dataMap);
    }

    private void loadBasalsAndTemps(DataMap dataMap) {
        ArrayList<DataMap> temps = dataMap.getDataMapArrayList("temps");
        if (temps != null) {
            tempWatchDataList = new ArrayList<>();
            for (DataMap temp : temps) {
                TempWatchData twd = new TempWatchData();
                twd.startTime = temp.getLong("starttime");
                twd.startBasal =  temp.getDouble("startBasal");
                twd.endTime = temp.getLong("endtime");
                twd.endBasal = temp.getDouble("endbasal");
                twd.amount = temp.getDouble("amount");
                tempWatchDataList.add(twd);
            }
        }
        ArrayList<DataMap> basals = dataMap.getDataMapArrayList("basals");
        if (basals != null) {
            basalWatchDataList = new ArrayList<>();
            for (DataMap basal : basals) {
                BasalWatchData bwd = new BasalWatchData();
                bwd.startTime = basal.getLong("starttime");
                bwd.endTime = basal.getLong("endtime");
                bwd.amount = basal.getDouble("amount");
                basalWatchDataList.add(bwd);
            }
        }
        ArrayList<DataMap> boluses = dataMap.getDataMapArrayList("boluses");
        if (boluses != null) {
            bolusWatchDataList = new ArrayList<>();
            for (DataMap bolus : boluses) {
                BolusWatchData bwd = new BolusWatchData();
                bwd.date = bolus.getLong("date");
                bwd.bolus = bolus.getDouble("bolus");
                bwd.carbs = bolus.getDouble("carbs");
                bwd.isSMB = bolus.getBoolean("isSMB");
                bwd.isValid = bolus.getBoolean("isValid");
                bolusWatchDataList.add(bwd);
            }
        }
        ArrayList<DataMap> predictions = dataMap.getDataMapArrayList("predictions");
        if (boluses != null) {
            predictionList = new ArrayList<>();
            for (DataMap prediction : predictions) {
                BgWatchData bwd = new BgWatchData();
                bwd.timestamp = prediction.getLong("timestamp");
                bwd.sgv = prediction.getDouble("sgv");
                bwd.color = prediction.getInt("color");
                predictionList.add(bwd);
            }
        }
    }

    public void addToWatchSet(DataMap dataMap) {
        ArrayList<DataMap> entries = dataMap.getDataMapArrayList("entries");
        if (entries != null) {
            bgDataList = new ArrayList<>();
            for (DataMap entry : entries) {
                double sgv = entry.getDouble("sgvDouble");
                double high = entry.getDouble("high");
                double low = entry.getDouble("low");
                long timestamp = entry.getLong("timestamp");
                int color = entry.getInt("color", 0);
                bgDataList.add(new BgWatchData(sgv, high, low, timestamp, color));
            }
        } else {
            double sgv = dataMap.getDouble("sgvDouble");
            double high = dataMap.getDouble("high");
            double low = dataMap.getDouble("low");
            long timestamp = dataMap.getLong("timestamp");
            int color = dataMap.getInt("color", 0);

            final int size = bgDataList.size();
            if (size > 0) {
                if (bgDataList.get(size - 1).timestamp == timestamp)
                    return; // Ignore duplicates.
            }

            bgDataList.add(new BgWatchData(sgv, high, low, timestamp, color));
        }

        // We use iterator instead for-loop because we iterate and remove on the go
        Iterator itr = bgDataList.iterator();
        while (itr.hasNext()) {
            BgWatchData entry = (BgWatchData)itr.next();
            if (entry.timestamp < (WearUtil.timestamp() - (Constants.HOUR_IN_MS * 5))) {
                itr.remove(); //Get rid of anything more than 5 hours old
            }
        }
    }
}

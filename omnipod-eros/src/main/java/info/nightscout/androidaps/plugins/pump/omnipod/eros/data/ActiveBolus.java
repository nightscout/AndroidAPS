package info.nightscout.androidaps.plugins.pump.omnipod.eros.data;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;

// Used for storing active bolus during bolus,
// so we can recover it and add it to treatments after the app crashed or got killed
// Storing DetailedBolusInfo itself is no good because it contains a reference to Context
// and to JSONObject, which are both not serializable
// TODO add tests
public class ActiveBolus {
    private long date;
    private long lastKnownBolusTime;
    private String eventType;
    private double insulin;
    private double carbs;
    private int source;
    private boolean isValid;
    private double glucose;
    private String glucoseType;
    private int carbTime;
    private String boluscalc;
    private long pumpId;
    private boolean isSMB;
    private long deliverAt;
    private String notes;

    public static ActiveBolus fromDetailedBolusInfo(DetailedBolusInfo detailedBolusInfo) {
        ActiveBolus activeBolus = new ActiveBolus();
        activeBolus.date = detailedBolusInfo.date;
        activeBolus.lastKnownBolusTime = detailedBolusInfo.lastKnownBolusTime;
        activeBolus.eventType = detailedBolusInfo.eventType;
        activeBolus.insulin = detailedBolusInfo.insulin;
        activeBolus.carbs = detailedBolusInfo.carbs;
        activeBolus.source = detailedBolusInfo.source;
        activeBolus.isValid = detailedBolusInfo.isValid;
        activeBolus.glucose = detailedBolusInfo.glucose;
        activeBolus.glucoseType = detailedBolusInfo.glucoseType;
        activeBolus.carbTime = detailedBolusInfo.carbTime;
        activeBolus.boluscalc = detailedBolusInfo.boluscalc == null ? null : detailedBolusInfo.boluscalc.toString();
        activeBolus.pumpId = detailedBolusInfo.pumpId;
        activeBolus.isSMB = detailedBolusInfo.isSMB;
        activeBolus.deliverAt = detailedBolusInfo.deliverAt;
        activeBolus.notes = detailedBolusInfo.notes;
        return activeBolus;
    }

    public DetailedBolusInfo toDetailedBolusInfo(AAPSLogger aapsLogger) {
        DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
        detailedBolusInfo.date = date;
        detailedBolusInfo.lastKnownBolusTime = lastKnownBolusTime;
        detailedBolusInfo.eventType = eventType;
        detailedBolusInfo.insulin = insulin;
        detailedBolusInfo.carbs = carbs;
        detailedBolusInfo.source = source;
        detailedBolusInfo.isValid = isValid;
        detailedBolusInfo.glucose = glucose;
        detailedBolusInfo.glucoseType = glucoseType;
        detailedBolusInfo.carbTime = carbTime;
        if (!StringUtils.isEmpty(boluscalc)) {
            try {
                detailedBolusInfo.boluscalc = new JSONObject(boluscalc);
            } catch (JSONException ex) {
                // ignore
                aapsLogger.warn(LTag.PUMP, "Could not parse bolusCalc string to JSON: " + boluscalc, ex);
            }
        }
        detailedBolusInfo.pumpId = pumpId;
        detailedBolusInfo.isSMB = isSMB;
        detailedBolusInfo.deliverAt = deliverAt;
        detailedBolusInfo.notes = notes;
        return detailedBolusInfo;
    }
}

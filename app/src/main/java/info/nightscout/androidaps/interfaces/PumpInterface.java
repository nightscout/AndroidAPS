package info.nightscout.androidaps.interfaces;

import org.json.JSONObject;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.client.data.NSProfile;

/**
 * Created by mike on 04.06.2016.
 */
public interface PumpInterface {

    boolean isTempBasalInProgress();
    boolean isExtendedBoluslInProgress();

    Integer getBatteryPercent();
    Integer getReservoirValue();

    // Upload to pump new basal profile
    void setNewBasalProfile(NSProfile profile);

    double getBaseBasalRate(); // base basal rate, not temp basal
    double getTempBasalAbsoluteRate();
    double getTempBasalRemainingMinutes();
    TempBasal getTempBasal();
    TempBasal getExtendedBolus();

    PumpEnactResult deliverTreatment(Double insulin, Integer carbs);
    PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes);
    PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes);
    PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes);
    PumpEnactResult cancelTempBasal();
    PumpEnactResult cancelExtendedBolus();

    // Status to be passed to NS
    JSONObject getJSONStatus();
    String deviceID();
}

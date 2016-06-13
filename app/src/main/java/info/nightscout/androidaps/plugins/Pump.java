package info.nightscout.androidaps.plugins;

import org.json.JSONObject;

import info.nightscout.androidaps.data.Result;
import info.nightscout.client.data.NSProfile;

/**
 * Created by mike on 04.06.2016.
 */
public interface Pump {

    boolean isTempBasalInProgress();
    boolean isExtendedBoluslInProgress();

    Integer getBatteryPercent();
    Integer getReservoirValue();

    // Upload to pump new basal profile
    void setNewBasalProfile(NSProfile profile);

    double getBaseBasalRate(); // base basal rate, not temp basal
    double getTempBasalAbsoluteRate();
    double getTempBasalRemainingMinutes();

    Result deliverTreatment(Double insulin, Double carbs);
    Result setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes);
    Result setTempBasalPercent(Integer percent, Integer durationInMinutes);
    Result setExtendedBolus(Double insulin, Integer durationInMinutes);
    Result cancelTempBasal();
    Result cancelExtendedBolus();

    JSONObject getJSONStatus();
}

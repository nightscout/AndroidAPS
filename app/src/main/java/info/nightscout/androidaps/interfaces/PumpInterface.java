package info.nightscout.androidaps.interfaces;

import android.content.Context;

import org.json.JSONObject;

import java.util.Date;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.client.data.NSProfile;

/**
 * Created by mike on 04.06.2016.
 */
public interface PumpInterface {

    boolean isInitialized();

    boolean isTempBasalInProgress();
    boolean isExtendedBoluslInProgress();

    // Upload to pump new basal profile
    int SUCCESS = 0;
    int FAILED = 1;
    int NOT_NEEDED = 2;
    int setNewBasalProfile(NSProfile profile);
    boolean isThisProfileSet(NSProfile profile);

    double getBaseBasalRate(); // base basal rate, not temp basal
    double getTempBasalAbsoluteRate();
    double getTempBasalRemainingMinutes();
    TempBasal getTempBasal(Date time);
    TempBasal getTempBasal();
    TempBasal getExtendedBolus();

    PumpEnactResult deliverTreatment(Double insulin, Integer carbs, Context context);
    void stopBolusDelivering();
    PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes);
    PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes);
    PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes);
    PumpEnactResult cancelTempBasal();
    PumpEnactResult cancelExtendedBolus();

    // Status to be passed to NS
    JSONObject getJSONStatus();
    String deviceID();

    PumpDescription getPumpDescription();
}
